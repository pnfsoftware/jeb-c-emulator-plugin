import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import com.pnfsoftware.jeb.client.HeadlessClientContext;
import com.pnfsoftware.jeb.core.exceptions.JebException;
import com.pnfsoftware.jeb.util.io.IO;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;

/**
 * Headless JEB client used to test and develop a back-end plugin.
 * 
 * @author Nicolas Falliere
 *
 */
public class Tester {
    private static final ILogger logger = GlobalLog.getLogger(Tester.class);

    static {
        try {
            // log all output to a temporary file
            GlobalLog.addDestinationStream(new PrintStream(new File(IO.getTempFolder(), "jeb-plugin-tester.log")));
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws JebException, IOException {
        // create a simple JEB client object; our subclass here is to provide an isDevMode() override and always
        // force development mode, regardless of the settings in jeb-client.cfg
        // (this client will make use of your [JEB]/bin/{jeb-engines.cfg,jeb-client.cfg} configuration files)
        HeadlessClientContext client = new HeadlessClientContext() {
            @Override
            public boolean isDevelopmentMode() {
                return true;
            }
        };

        // initialize and start the client
        client.initialize(args);
        client.start();

        // hot-load the plugin that is being worked on:
        String classpath = new File("bin").getAbsolutePath();
        String classname = SamplePlugin.class.getName();
        client.getEnginesContext().getPluginManager().load(classpath, classname);
        // note that an alternative to the 3 lines above is to specify that plugin in the your jeb-engines.cfg file
        // (see .DevPluginClasspath and .DevPluginClassnames keys)

        try {
            testPlugin(client);
        }
        catch(Exception e) {
            logger.catching(e);
        }

        client.stop();
    }

    static void testPlugin(HeadlessClientContext client) throws Exception {
        // TODO
    }
}
