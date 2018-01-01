package model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Command
{

    private final String command;
    private String result = "";

    public Command(String command)
    {
	this.command = command;
    }

    public boolean execute()
    {
	String[] output = execute(command);

	if (output == null)
	{
	    System.err.println("Ooops! Command execution (" + command + ") with no result.");
	    return false;
	}
	else if (output[1].length() > 0)
	{
	    System.err.println("Ooops! Command execution (" + command + ") failed with message: ");
	    System.err.println(output[1]);
	    return false;
	}
	else
	{
	    result = output[0];
	    return true;
	}
    }

    private String[] execute(String command)
    {
	Process process;
	try
	{
	    process = Runtime.getRuntime().exec(command);

	    return execute(process);
	}
	catch (NumberFormatException ex)
	{
	    System.err.println(ex);
	}
	catch (IOException | InterruptedException ex)
	{
	    System.err.println(ex);
	}

	return null;
    }

    private String[] execute(Process process) throws InterruptedException, IOException
    {
	int r = process.waitFor();

	InputStream ns = process.getInputStream();// normal output stream
	InputStream es = process.getErrorStream();// error output stream

	String[] output = new String[2];
	output[0] = read(ns);
	output[1] = read(es);

	ns.close();
	es.close();

	return output;
    }

    private String read(InputStream is)
    {
	try
	{
	    InputStreamReader isr = new InputStreamReader(is);
	    BufferedReader br = new BufferedReader(isr);

	    StringBuilder output = new StringBuilder();
	    String line;
	    while ((line = br.readLine()) != null)
	    {
		output.append("\n");
		output.append(line);
	    }

	    br.close();
	    isr.close();

	    if (output.length() > 0)
	    {
		return output.substring(1);// remove head new line
	    }
	}
	catch (IOException ex)
	{
	    System.err.println(ex);
	}

	return "";
    }

    @Override
    public String toString()
    {
	return result;
    }
}
