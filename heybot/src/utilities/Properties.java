package utilities;

import java.io.File;
import java.io.FileNotFoundException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.FileBasedConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

/**
 * An extended implementation of java.util.Properties
 *
 * @author onur
 */
public class Properties
{

    private Configuration content;

    public Properties()
    {

    }

    public void load(String file) throws ConfigurationException, FileNotFoundException
    {
	Parameters params = new Parameters();
	File propertiesFile = new File(file);

	FileBasedConfigurationBuilder<FileBasedConfiguration> builder
		= new FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration.class)
		.configure(params.fileBased()
			.setFile(propertiesFile));

	builder.setAutoSave(true);
	content = builder.getConfiguration();
    }

    public String getProperty(String key)
    {
	return content.getString(key);
    }

    public void setProperty(String key, String value)
    {
	content.setProperty(key, value);
    }

}
