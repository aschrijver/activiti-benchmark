package be.jorambarrez.activiti.benchmark;

import be.jorambarrez.activiti.benchmark.execution.*;
import be.jorambarrez.activiti.benchmark.output.BenchmarkOuput;
import be.jorambarrez.activiti.benchmark.output.BenchmarkResult;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.activiti.engine.impl.util.LogUtil;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Main class that contains the logic to execute the benchmark.
 * 
 * @author jbarrez
 */
public class Benchmark {

  static {
    LogUtil.readJavaUtilLoggingConfigFromClasspath();
  }

  private static String[] PROCESSES = {
    "process01", 
    "process02", 
    "process03", 
    "process04", 
    "process05" 
  };
  
  private static int maxNrOfThreadsInThreadPool;
  
  private static List<BenchmarkResult> fixedPoolSequentialResults = new ArrayList<BenchmarkResult>();
  private static List<BenchmarkResult> fixedPoolRandomResults = new ArrayList<BenchmarkResult>();

  
  public static void main(String[] args) {

      long start = System.currentTimeMillis();

      if (!validateParams(args)) {
          System.exit(1);
      }

      String config = args[0];
      boolean history = args[1].equals("history");
      int nrOfExecutions = Integer.valueOf(args[2]);
      maxNrOfThreadsInThreadPool = Integer.valueOf(args[3]);

      ProcessEngine processEngine = getProcessEngine(config);
      executeBenchmarks(processEngine, history, nrOfExecutions, maxNrOfThreadsInThreadPool);
      writeHtmlReport();

      System.out.println("Benchmark completed. Ran for "
              + ((System.currentTimeMillis() - start)/1000L) + " seconds");
  }

  private static ProcessEngine getProcessEngine(String configuration) {
        if (configuration.equals("default")) {
            return StandaloneProcessEngineConfiguration
                    .createProcessEngineConfigurationFromResource("activiti.cfg.xml")
                    .buildProcessEngine();
        } else if (configuration.equals("spring")) {
            ClassPathXmlApplicationContext appCtx = new ClassPathXmlApplicationContext("spring-context.xml");
            return appCtx.getBean(ProcessEngine.class);
        }
        throw new RuntimeException("Invalid config: only 'default' and 'spring' are supported");
    }

    private static void executeBenchmarks(ProcessEngine processEngine, boolean history, int nrOfProcessExecutions, int maxNrOfThreadsInThreadPool) {

        // Deploy test processes
        for (String process : PROCESSES) {
            processEngine.getRepositoryService().createDeployment()
                    .addClasspathResource(process + ".bpmn20.xml").deploy();
        }

        // Single thread benchmark
        System.out.println(new Date() + " - benchmarking with one thread.");
        BenchmarkExecution singleThreadBenchmark = new BasicBenchmarkExecution(processEngine);
        fixedPoolSequentialResults.add(singleThreadBenchmark.sequentialExecution(PROCESSES, nrOfProcessExecutions, history));
        fixedPoolRandomResults.add(singleThreadBenchmark.randomExecution(PROCESSES, nrOfProcessExecutions, history));

        // Multiple threads - fixed pool benchmark
        for (int nrOfWorkerThreads = 2; nrOfWorkerThreads <= maxNrOfThreadsInThreadPool; nrOfWorkerThreads++) {

            System.out.println(new Date() + " - benchmarking with fixed threadpool of " + nrOfWorkerThreads + " threads.");
            BenchmarkExecution fixedPoolBenchMark = new FixedThreadPoolBenchmarkExecution(processEngine, nrOfWorkerThreads);
            fixedPoolSequentialResults.add(fixedPoolBenchMark.sequentialExecution(PROCESSES, nrOfProcessExecutions, history));
            fixedPoolRandomResults.add(fixedPoolBenchMark.randomExecution(PROCESSES, nrOfProcessExecutions, history));

        }

        // Multiple threads - cached pool
        // commented -> quickly gives connection problems due to creation of a lot threads
        /*
        System.out.println("Benchmarking with cached threadpool.");
        CachedThreadPoolBenchmarkExecution cachedPoolBenchmark =
          new CachedThreadPoolBenchmarkExecution(processEngine, PROCESSES, nrOfExecutions, false);
        BenchmarkResult cachedPoolNonRandomResult = cachedPoolBenchmark.nonRandomExecute();
        BenchmarkResult cachedPoolRandomResult = cachedPoolBenchmark.randomExecute();
        */
    }

    private static void writeHtmlReport() {
        BenchmarkOuput output = new BenchmarkOuput();
        output.start("Activiti 5.9 basic benchmark results");

        for (int i = 1; i <= maxNrOfThreadsInThreadPool; i++) {
            output.addBenchmarkResult("Fixed thread pool (" + i + "threads), sequential", fixedPoolSequentialResults.get(i - 1));
        }
        output.generateChartOfPreviousAddedBenchmarkResults();

        //output.addBenchmarkResult("Cached thread pool, non randomized", cachedPoolNonRandomResult);

        for (int i = 1; i <= maxNrOfThreadsInThreadPool; i++) {
            output.addBenchmarkResult("Fixed thread pool (" + i + "threads), randomized", fixedPoolRandomResults.get(i - 1));
        }
        output.generateChartOfPreviousAddedBenchmarkResults();

        //output.addBenchmarkResult("Cached thread pool, randomized", cachedPoolRandomResult);

        output.writeOut();
    }

  /**
   * Validates the commandline arguments.
   * 
   * @return True if they are OK
   */
  private static boolean validateParams(String[] args) {

      // length check
      if (args.length != 4) {
          System.err.println("Wrong number of arguments");
          System.err.println("Usage: java -Xms512M -XmX1024M -jar activiti-basic-benchmark.jar " + "<default|spring> <history|no-history> <nr_of_executions> <max_nr_of_threads_in_threadpool>");
          return false;
      }

      // argument type check
      if (!args[0].equals("default") && !args[0].equals("spring")) {
          System.err.println("Invalid configuration option: only default|spring are currently supported");
          return false;
      }

      if (!args[1].equals("history") && !args[1].equals("no-history")) {
          System.err.println("Invalid history option: only history|no-history are currently supported");
          return false;
      }

      try {
          Integer.parseInt(args[2]);
          Integer.parseInt(args[3]);
      } catch (NumberFormatException e) {
          System.err.println("Wrong argument type: use integers only");
          return false;
      }

      return true;
  }

}
