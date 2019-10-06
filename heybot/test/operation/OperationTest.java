package operation;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.Assert;
import org.junit.Test;
import utilities.Properties;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

public class OperationTest
{

    @Test
    public void getParameterIntArray() {
        Properties props = getEmptyProperties();

        Operation operation = new Release();
        String pName = getParameterName(operation, "PARAMETER_VERSION_ID");
        int[] pValue;

        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should be optional.", new int[0], pValue);

        props.setProperty(pName, "");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Can be empty.", new int[0], pValue);

        props.setProperty(pName, "1");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Can be a single value.", new int[]{1}, pValue);

        props.setProperty(pName, "1,2,3");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Can have multiple values.", new int[]{1, 2, 3}, pValue);

        props.setProperty(pName, "1,2a,3b");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore invalid values.", new int[]{1}, pValue);

        props.setProperty(pName, "1,2a,");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore invalid values.", new int[]{1}, pValue);

        props.setProperty(pName, "1,2a,,,");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore invalid values.", new int[]{1}, pValue);

        props.setProperty(pName, ",2a,,,");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore invalid values.", new int[]{}, pValue);

        props.setProperty(pName, "2s");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore invalid values.", new int[]{}, pValue);

        props.setProperty(pName, "   2   ");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore whitespace around values.", new int[]{2}, pValue);

        props.setProperty(pName, "1 ,   2   ");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore whitespace around values.", new int[]{1, 2}, pValue);

        props.setProperty(pName, "1.1");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore float values.", new int[]{}, pValue);

        props.setProperty(pName, "24278287487878788");
        pValue = operation.getParameterIntArray(props, pName);
        Assert.assertArrayEquals("Should ignore invalid int values.", new int[]{}, pValue);
    }

    private Properties getEmptyProperties() {
        Properties props = new Properties();
        String file = "out/testOperationConf.hb";

        File f = new File(file);
        if (f.exists()) {
            Assert.assertTrue("Existing config file should be deleted.", f.delete());
        }
        try {
            f.createNewFile();
        } catch (IOException e1) {
            // ignored intentionally
        }
        try {
            props.load(file);
        } catch (ConfigurationException e) {
            // ignored intentionally
        }
        Assert.assertTrue("Test operation config file should be loaded.", props.isLoadedSuccessfully());
        return props;
    }

    private String getParameterName(Operation operation, String field) {
        try {
            final Field declaredField = operation.getClass().getDeclaredField(field);
            declaredField.setAccessible(true);
            return declaredField.get("").toString();
        } catch (NoSuchFieldException e) {
            // ignored
        } catch (IllegalAccessException e) {
            // ignored
        }
        return "";
    }
}
