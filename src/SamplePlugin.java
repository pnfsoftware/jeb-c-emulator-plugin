import java.util.Map;

import com.pnfsoftware.jeb.core.AbstractEnginesPlugin;
import com.pnfsoftware.jeb.core.IEnginesContext;
import com.pnfsoftware.jeb.core.IPlugin;
import com.pnfsoftware.jeb.core.IPluginInformation;
import com.pnfsoftware.jeb.core.PluginInformation;
import com.pnfsoftware.jeb.core.Version;
import com.pnfsoftware.jeb.util.logging.GlobalLog;

/**
 * Sample plugin.
 * <p>
 * This class must implement one of {@link IPlugin}'s sub-interfaces or extend one of its abstract
 * implementations.
 * 
 * @author Nicolas Falliere
 *
 */
public class SamplePlugin extends AbstractEnginesPlugin {
                               // ^ change that for another plugin type

    @Override
    public void load(IEnginesContext context) {
        GlobalLog.getLogger().info("Loading sample plugin");
    }

    @Override
    public void execute(IEnginesContext context, Map<String, String> params) {
        GlobalLog.getLogger().info("Executing sample plugin");
    }

    @Override
    public IPluginInformation getPluginInformation() {
        return new PluginInformation("SamplePlugin", "A sample plugin", "Author", Version.create(1, 0, 0));
    }
}
