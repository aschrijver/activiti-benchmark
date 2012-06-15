package be.jorambarrez.activiti.benchmark.output;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class BenchmarkOuput {

    private String folderName;

    private StringBuilder strb;

    private ArrayList<BenchmarkResult> results;

    public BenchmarkOuput() {
        DateFormat df = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss");
        this.folderName = "benchmark_report_" + df.format(new Date());
        File folder = new File(folderName);
        folder.mkdir();

        this.strb = new StringBuilder();
        this.results = new ArrayList<BenchmarkResult>();
    }

    public void start(String outputTitle) {
        strb.append("<html><body>");
        strb.append("<style TYPE='text/css'>"
                + "body{line-height: 1.6em;}"
                + ".minimalist{font-family: 'Lucida Sans Unicode', "
                + "'Lucida Grande', Sans-Serif;font-size: 20px;background: #fff;"
                + "margin: 45px;width: 480px;border-collapse: collapse;text-align: left;}"
                + ".minimalist th{font-size: 14px;font-weight: normal;color: #039;"
                + "padding: 10px 8px;border-bottom: 2px solid #6678b1;}"
                + ".minimalist td{color: #669;padding: 9px 8px 0px 8px;}"
                + ".minimalist tbody tr:hover td{color: #009;}"
                + "</style>");
        strb.append("<img src='http://activiti.org/images/activiti_logo.png' />");
        strb.append("<h2>" + outputTitle + "</h2>");
    }

    public void addBenchmarkResult(String title, BenchmarkResult benchmarkResult) {
        results.add(benchmarkResult);

        strb.append("<h3>" + title + "</h3><hr/>");

        strb.append("<table class='minimalist'>");
        strb.append("<thead><tr>");
        strb.append("<th scope='col'>PROCESS</th><th scope='col'>NR OF EXECUTIONS</th>" +
                "<th scope='col'>TOTAL TIME (ms)</th><th scope='col'>AVERAGE (ms)</th>" +
                "<th scope='col'>THROUGHPUT/SEC</th><th scope='col'>THROUGHPUT/HOUR</th></tr>");
        strb.append("</tr></thead>");

        for (String process : benchmarkResult.getProcesses()) {
            strb.append("<tr>");
            strb.append("<td>" + process + "</td>");


            strb.append("<td align='center'>" + benchmarkResult.getNrOfExecutions(process) + "</td>");
            strb.append("<td>" + benchmarkResult.getTotalTime(process) + "</td>");
            strb.append("<td>" + benchmarkResult.getAverage(process) + "</td>");
            strb.append("<td align='right'>" + benchmarkResult.getThroughputPerSecond(process) + "</td>");
            strb.append("<td align='right'>" + benchmarkResult.getThroughputPerHour(process) + "</td>");

            strb.append("</tr>");
        }

        strb.append("</table>");


        if (benchmarkResult.processesRandomized()) {
            strb.append("<br/>");
            strb.append("<h3>Process runs</h3><hr/>");
            strb.append("<ul>");
            Map<String, Integer> processCounts = benchmarkResult.getRandomizedProcessesExecutionCounts();
            for (String process : processCounts.keySet()) {
                strb.append("<li>" + process + " : " + processCounts.get(process) + " executions</li>");
            }
            strb.append("</ul>");
            strb.append("<br/>");
        }
    }

    /**
     * Method name says it all!
     */
    public void generateChartOfPreviousAddedBenchmarkResults() {
        strb.append("<h4>Result chart</h4>");

        // Create dataset for chart
        HashMap<String, HashMap<Integer, Double>> data = new HashMap<String, HashMap<Integer, Double>>(); // map<processName, map<nrOfThreads, avg time>>
        for (BenchmarkResult benchmarkResult : results) {
            for (String process : benchmarkResult.getProcesses()) {
                if (data.get(process) == null) {
                    data.put(process, new HashMap<Integer, Double>());
                }
                data.get(process).put(benchmarkResult.getNrOfThreads(), benchmarkResult.getAverage(process));
            }
        }


        JFreeChart chart = null;
        if (data.size() == 1) { // only 1 series -> barchart
            chart = generateBarChart(data);
        } else {
            chart = generateLineChart(data);
        }

        // Write image
        FileOutputStream fos = null;
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss_SSS");
        String chartFileName = "chart-" + dateFormat.format(new Date()) + ".png";
        try {
            fos = new FileOutputStream(new File(folderName + "/" + chartFileName));
            ChartUtilities.writeChartAsPNG(fos, chart, 800, 400);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Include image in html
        strb.append("<img src=" + chartFileName + "/>");

        // Empty the previous results
        results.clear();
    }

    protected JFreeChart generateLineChart(HashMap<String, HashMap<Integer, Double>> data) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        for (String process : data.keySet()) {
            HashMap<Integer, Double> processData = data.get(process);
            XYSeries series = new XYSeries(process);
            for (Integer threads : processData.keySet()) {
                series.add(threads, processData.get(threads));
            }
            dataset.addSeries(series);
        }

        // Generate chart
        final JFreeChart chart = ChartFactory.createXYLineChart(
                "",                       // chart title
                "Threads",                // x axis label
                "avg time (ms)",          // y axis label
                dataset,                  // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,                     // tooltips
                false                     // urls
        );

        chart.setBackgroundPaint(Color.white);

        // get a reference to the plot for further customisation...
        final XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);

        final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesLinesVisible(0, false);
        renderer.setSeriesShapesVisible(1, false);
        plot.setRenderer(renderer);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        return chart;
    }

    protected JFreeChart generateBarChart(HashMap<String, HashMap<Integer, Double>> data) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        for (String process : data.keySet()) {
            HashMap<Integer, Double> processData = data.get(process);
            for (Integer threads : processData.keySet()) {
                dataset.setValue(processData.get(threads), process, threads);
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "",
                "Threads",
                "acg time (ms)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false);

        return chart;
    }

    public void writeOut() {
        strb.append("</body></html>");

        BufferedWriter writer = null;
        try {
            String outputFile = folderName + "/benchmark_report.html";
            writer = new BufferedWriter(new FileWriter(new File(outputFile)));
            writer.write(strb.toString());
            System.out.println("Benchmark report written to " + outputFile);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
