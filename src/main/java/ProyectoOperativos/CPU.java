/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package main;


import interfaz.SimuladorInterfaz;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import structures.LinkedList;
import structures.Node;

/**
 *
 * @author marco
 */
public class CPU extends Thread {
    
    private int numCPUs;
    private LinkedList<CPU> cpus;
    private MemoriaPrincipal memoria;
    private Planificador planificador;
    private int cicloReloj;
    private GestorConfiguracion gestor;
    private SimuladorInterfaz si;
//    private boolean quantum;
    private boolean enEjecucion; // Bandera para detener el kernel
        
    public CPU(GestorConfiguracion gestor, SimuladorInterfaz si) {
        this.numCPUs = gestor.getNumCPUs();
        this.cpus = new LinkedList<CPU>();
        this.memoria = new MemoriaPrincipal(this);
        this.planificador = new Planificador(memoria, this);
        this.cicloReloj = 0;
        this.gestor = gestor;
        this.si = si;
//        this.quantum = false;
        this.enEjecucion = true;

        // Crear y asociar CPUs
        for (int i = 1; i <= numCPUs; i++) {
            CPU cpu = new CPU(i, this, gestor.getQuantum());
            this.cpus.add(cpu);
            cpu.start(); // Iniciar cada CPU una sola vez
        }
    }
    
    @Override    
    public void run() {
        System.out.println("Cargando " + gestor.getProcesosCargados().getSize() + " procesos.");
        if (planificador.getAlgoritmoActual().equals("Feedback")) {
            memoria.setColaQ1(gestor.getProcesosCargados());
        } else {
            memoria.setColaDeListos(gestor.getProcesosCargados());
        }
        
//         Usamos Timer para actualizar la interfaz cada 100ms
            Timer timer = new Timer(100, e -> actualizarInterfaz());
            timer.start();
        
        System.out.println("Simulación iniciada con " + numCPUs + " CPU(s).");
        while (enEjecucion) {
            
            cicloReloj++;
            
            Node<Proceso> aux2 = memoria.getColaListos().getHead();
            while (aux2 != null) {
                Proceso proceso = aux2.getData();
                proceso.getPCB().incrementarTiempoEspera();
                
                aux2 = aux2.getNext();
            }
            
            System.out.println("\n[Ciclo de reloj: " + cicloReloj + "]");
            if (!isFeedback()) {
                System.out.println("Total en listos: " + memoria.getCantidadListos());
            } else {
                System.out.println("Total en cola Q1: " + memoria.getCantidadQ1());
                System.out.println("Total en cola Q2: " + memoria.getCantidadQ2());
                System.out.println("Total en cola Q3: " + memoria.getCantidadQ3());
                
            }
            System.out.println("Total en bloqueados: " + memoria.getCantidadBloqueados());
            System.out.println("Total en terminados: " + memoria.getCantidadTerminados());
            
            Node<CPU> aux = cpus.getHead();
            while (aux != null) {
                CPU cpu = aux.getData();
                if (!cpu.estaLibre()) {
                    System.out.println("CPU " + cpu.getCpuId() + " ejecuta: " + cpu.getProcesoActual().getPCB().getNombre());
                } else {
                    System.out.println("CPU " + cpu.getCpuId() + " ejecuta: [ ]");
                }
                
                aux = aux.getNext();
            }
            
            manejarProcesos();
            manejarInterrupciones();
            
            if (si == null) {
                throw new IllegalStateException("La interfaz de simulación (SimuladorInterfaz) no ha sido inicializada correctamente.");
            }

//            actualizarInterfaz();
            
            
            try {
                Thread.sleep(gestor.getDuracionCiclo());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            

            // Detener la simulación si no hay procesos pendientes
            if (memoria.todosLosProcesosFinalizados(cpus)) {
                System.out.println("Todos los procesos han terminado. Finalizando simulación.");
                timer.stop();
                detenerKernel();
            }
        }
        
    }
    
    // Asignar procesos a CPUs
    private void manejarProcesos() {
        Node<CPU> aux = cpus.getHead();
        while (aux != null) {
            CPU cpu = aux.getData();
            if (cpu.estaLibre()) {
                
                Proceso proceso = planificador.seleccionarSiguienteProceso();
                if (proceso != null) {
                    cpu.asignarProceso(proceso);
                } else {
//                    System.out.println("⚠ No hay procesos en la cola de listos.");
                }
            }
            aux = aux.getNext();
        }
    }
    
    // Manejar interrupciones de procesos bloqueados
    private void manejarInterrupciones() {
        memoria.desbloquearProcesos();
    }
    
    // Finaliza la ejecución del Kernel
    public void detenerKernel() {
        actualizarInterfaz();
        enEjecucion = false;
        System.out.println("Deteniendo todas las CPU...");

        Node<CPU> aux = cpus.getHead();
        while (aux != null) {
            aux.getData().detenerCPU(); // Llama a detenerCPU() en cada CPU
            aux = aux.getNext();
        }

        System.out.println("Simulación finalizada.");
    }
    
    // Interrumpe el CPU que ejecuta el proceso con mayor cantidad de Instrucciones Restantes;
    public void forzarProceso() {
        Node<CPU> aux = cpus.getHead();
        Node<Proceso> aux2 = memoria.getColaListos().getHead();
        CPU cpu;
        Proceso proceso = planificador.seleccionarProcesoSRT(true);
        CPU longest = null;
        while (aux != null) {
            cpu = aux.getData();
            if (cpu.estaLibre()) {
                return;
            }
            if (proceso.getPCB().getInstruccionesRestantes() < cpu.getProcesoActual().getPCB().getInstruccionesRestantes()) {
                if (longest != null) {
                    if (longest.getProcesoActual().getPCB().getInstruccionesRestantes() < cpu.getProcesoActual().getPCB().getInstruccionesRestantes()) {
                        longest = cpu;
                    }
                } else {
                    longest = cpu;
                }
            }

            aux = aux.getNext();
        }
        if (longest != null) {
            longest.generarInterrupcion();
        }
    }
    
    // Obtener acceso a la memoria principal
    public MemoriaPrincipal getMemoria() {
        return memoria;
    }
    
    public void bloquearProceso(Proceso proceso) {
        planificador.bloquearProceso(proceso);
    }
    
    public void finalizarProceso(Proceso proceso) {
        planificador.finalizarProceso(proceso);
    }
    
    public void prepararProceso(Proceso proceso) {
        planificador.prepararProceso(proceso);
    }

    public boolean isQuantum() {
        return (planificador.getAlgoritmoActual().equals("Feedback") || planificador.getAlgoritmoActual().equals("RR"));
    }
    
    public boolean isFeedback() {
        return (planificador.getAlgoritmoActual().equals("Feedback"));
    }

//    public void setUseQuantum(boolean quantum) {
//        this.quantum = quantum;
//    }

    public GestorConfiguracion getGestor() {
        return gestor;
    }

    public void setGestor(GestorConfiguracion gestor) {
        this.gestor = gestor;
    }

    public Planificador getPlanificador() {
        return planificador;
    }

    public void setPlanificador(Planificador planificador) {
        this.planificador = planificador;
    }
    
    public void decrementarPrioridad(Proceso proceso) {
        PCB pcb = proceso.getPCB();
        switch (pcb.getPrioridad()) {
            case "Q1":
                pcb.setPrioridad("Q2");
                break;
            case "Q2":
                pcb.setPrioridad("Q3");
                break;
        }
    }
    
    public void actualizarInterfaz() {
//        try {
            SwingUtilities.invokeLater(() -> {
                // Actualizar el ciclo de reloj
                si.setCicloReloj(cicloReloj);

                si.limpiarTablas();

                System.out.println("AQUI");
                // Actualizar colas de procesos
                Node<Proceso> aux = memoria.getColaListos().getHead();
                while (aux != null) {
                    Proceso p = aux.getData();
                    si.agregarProcesoATablaListos(p);
                    aux = aux.getNext();
                }

                aux = memoria.getColaBloqueados().getHead();
                while (aux != null) {
                    Proceso p = aux.getData();
                    si.agregarProcesoATablaBloqueados(p);
                    aux = aux.getNext();
                }

                aux = memoria.getColaTerminados().getHead();
                while (aux != null) {
                    Proceso p = aux.getData();
                    si.agregarProcesoATablaTerminados(p);
                    aux = aux.getNext();
                }

                // Actualizar CPUs en ejecución
                Node<CPU> auxCpu = cpus.getHead();
                while (auxCpu != null) {
                    CPU cpu = auxCpu.getData();
        //            String nombreProceso = cpu.estaLibre() ? "IDLE" : cpu.getProcesoActual().getPCB().getNombre();
        //            int instruccionesRestantes = cpu.estaLibre() ? -1 : cpu.getProcesoActual().getPCB().getInstruccionesRestantes();
                    si.agregarProcesoATablaCPUs(cpu);
                    auxCpu = auxCpu.getNext();
                }
                });
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }

    public int getCicloReloj() {
        return cicloReloj;
    }

    public void setCicloReloj(int cicloReloj) {
        this.cicloReloj = cicloReloj;
    }

    public LinkedList<CPU> getCpus() {
        return cpus;
    }

    public void setCpus(LinkedList<CPU> cpus) {
        this.cpus = cpus;
    }
}
