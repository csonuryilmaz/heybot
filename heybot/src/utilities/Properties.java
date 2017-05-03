package utilities;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.PropertiesConfigurationLayout;
import org.apache.commons.configuration2.ex.ConfigurationException;

/**
 * An extended implementation of java.util.Properties
 *
 * @author onur
 */
public class Properties
{

    private final PropertiesConfigurationLayout builder = new PropertiesConfigurationLayout();
    private final PropertiesConfiguration content = new PropertiesConfiguration();

    public Properties()
    {

    }

    public void load(String file) throws ConfigurationException, FileNotFoundException
    {
	builder.load(content, new FileReader(file));
    }

    public String getProperty(String key)
    {
	return content.getString(key);
    }

    public void setProperty(String key, String value)
    {
	content.setProperty(key, value);
    }

    public void store(String file) throws ConfigurationException, IOException
    {
	builder.setHeaderComment("Operation configuration file for heybot." + System.getProperty("line.separator")
		+ "See <URL:https://github.com/csonuryilmaz/heybot/releases/latest> for detailed information.");

	builder.setFooterComment("Last run was on " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

	builder.save(content, new FileWriter(file));
    }

}
