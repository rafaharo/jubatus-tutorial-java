package example;

import example.classifier.ClassifierClient;
import example.classifier.util.DatumBuilder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import us.jubat.classifier.ConfigData;
import us.jubat.classifier.Datum;
import us.jubat.classifier.EstimateResult;
import us.jubat.classifier.TupleStringDatum;

/**
 * <p>
 * see <a href="https://github.com/jubatus/jubatus-tutorial-python/blob/master/tutorial.py">
 *  jubatus / jubatus-tutorial-python / tutorial.py</a>
 * </p>
 *
 * @author <a href="https://github.com/naokikimura">naokikimura</a>
 */
public class App {
    public static final String DEFAULT_ALGORITHM = "PA";
    public static final String DEFAULT_INSTANCE_NAME = "tutorial";
    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    public static final String DEFAULT_SERVER_PORT = "9199";

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    public static void main(String[] args) throws Exception {
        Options options = buildOptions();
        CommandLineParser parser = new PosixParser();
        CommandLine cl = parser.parse(options, args);

        if (cl.hasOption("?")) {
            HelpFormatter help = new HelpFormatter();
            help.printHelp(App.class.getName(), options, true);
            return;
        }

        String id = "tutorial";
        String name = cl.getOptionValue("n", DEFAULT_INSTANCE_NAME);

        String host = cl.getOptionValue("s", DEFAULT_SERVER_HOST);
        int port = Integer.parseInt(cl.getOptionValue("p", DEFAULT_SERVER_PORT));
        double timeout_sec = 10.0;
        try (ClassifierClient client = new ClassifierClient(host, port, timeout_sec)) {
            ConfigData conf = new ConfigData();
            conf.method = cl.getOptionValue("a", DEFAULT_ALGORITHM);
            conf.config = loadConverter(App.class.getResource("converter.json")).toString();

            client.set_config(name, conf);

            LOGGER.config(toJSONString(client.get_config(name)));
            LOGGER.fine(toJSONString(client.get_status(name)));

            train(client, name, App.class.getResource("train.dat"));

            client.save(name, id);
            client.load(name, id);

            client.set_config(name, conf);
            LOGGER.config(toJSONString(client.get_config(name)));

            classify(client, name, App.class.getResource("test.dat"));
        }
    }

    private static String toJSONString(Map<String, Map<String, String>> status) {
        return JSONObject.toJSONString(status);
    }

    private static String toJSONString(ConfigData conf) {
        return String.format(
                "{\"method\":\"%s\",\"config\":%s}",
                conf.method, conf.config);
    }

    private static void train(ClassifierClient client, String name, URL url) throws IOException {
        InputStream is = url.openStream();
        try {
            DatumBuilder builder = new DatumBuilder();
            LineIterator it = IOUtils.lineIterator(is, "UTF-8");
            while (it.hasNext()) {
                String[] row = it.nextLine().split(",", 2);
                String label = row[0];
                String file = row[1];

                URL resource = App.class.getResource(file);
                if (resource == null) {
                    throw new FileNotFoundException("not found " + file);
                }
                Datum datum = builder.setTuple("message", loadMessage(resource)).create();
                TupleStringDatum tuple = new TupleStringDatum();
                tuple.first = label;
                tuple.second = datum;
                client.train(name, Arrays.asList(tuple));
                LOGGER.fine(toJSONString(client.get_status(name)));
            }
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static void classify(ClassifierClient client, String name, URL url) throws IOException {
        InputStream is = url.openStream();
        try {
            DatumBuilder builder = new DatumBuilder();
            LineIterator it = IOUtils.lineIterator(is, "UTF-8");
            while (it.hasNext()) {
                String[] row = it.nextLine().split(",", 2);
                String label = row[0];
                String file = row[1];

                URL resource = App.class.getResource(file);
                if (resource == null) {
                    throw new FileNotFoundException("not found " + file);
                }
                Datum datum = builder.setTuple("message", loadMessage(resource)).create();
                List<List<EstimateResult>> ans = client.classify(name, Arrays.asList(datum));
                for (List<EstimateResult> e : ans) {
                    EstimateResult estm = getMostLikely(e);
                    String result = label.equals(estm.label) ? "OK" : "NG";
                    System.out.printf("%s,%s,%s,%f%n",
                            result, label, estm.label, estm.prob);
                }
            }
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static String loadMessage(URL url) throws IOException {
        InputStream is = url.openStream();
        try {
            return IOUtils.toString(is);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static EstimateResult getMostLikely(List<EstimateResult> EstimateResults) {
        return Collections.max(EstimateResults, new Comparator<EstimateResult>() {

            @Override
            public int compare(EstimateResult o1, EstimateResult o2) {
                return o1.prob == o2.prob ? 0 : o1.prob < o2.prob ? -1 : 1;
            }
        });
    }

    private static Options buildOptions() {
        Options options = new Options();

        OptionBuilder.withDescription("Display help information");
        OptionBuilder.withArgName("help");
        OptionBuilder.withLongOpt("help");
        options.addOption(OptionBuilder.create('?'));

        OptionBuilder.withDescription("Server host (default: " + DEFAULT_SERVER_HOST + ")");
        OptionBuilder.withArgName("host");
        OptionBuilder.withLongOpt("server_host");
        OptionBuilder.withType(String.class);
        OptionBuilder.hasArg();
        options.addOption(OptionBuilder.create("h"));

        OptionBuilder.withDescription("Server port (default: " + DEFAULT_SERVER_PORT + ")");
        OptionBuilder.withArgName("port");
        OptionBuilder.withLongOpt("server_port");
        OptionBuilder.withType(Number.class);
        OptionBuilder.hasArg();
        options.addOption(OptionBuilder.create("p"));

        OptionBuilder.withDescription("Instance name (default: " + DEFAULT_INSTANCE_NAME + ")");
        OptionBuilder.withArgName("name");
        OptionBuilder.withLongOpt("name");
        OptionBuilder.withType(String.class);
        OptionBuilder.hasArg();
        options.addOption(OptionBuilder.create("n"));

        OptionBuilder.withDescription("Algorithm (default: " + DEFAULT_ALGORITHM + ")");
        OptionBuilder.withArgName("algorithm");
        OptionBuilder.withLongOpt("algo");
        OptionBuilder.withType(String.class);
        OptionBuilder.hasArg();
        options.addOption(OptionBuilder.create("a"));

        return options;
    }

    private static JSONObject loadConverter(URL url) throws IOException, ParseException {
        InputStream is = url.openStream();
        try {
            JSONParser jsonParser = new JSONParser();
            return (JSONObject) jsonParser.parse(new InputStreamReader(is));
        } finally {
            IOUtils.closeQuietly(is);
        }
    }
}
