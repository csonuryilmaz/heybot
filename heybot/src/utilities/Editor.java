package utilities;

import model.Command;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Editor
{

    private final Properties prop;
    private final String editor;

    public Editor(String workspace, String editor) throws IOException, ConfigurationException {
        String config = workspace + "/config.properties";
        createConfigIfNotExists(config);

        prop = new Properties();
        prop.load(config);

        this.editor = editor;
    }

    private void createConfigIfNotExists(String path) throws IOException {
        File config = new File(path);
        if (!config.exists()) {
            if (!config.createNewFile()) {
                throw new IOException("Ooops! Global config.properties could not be created. \n"
                    + config.getAbsolutePath());
            }
        }
    }

    public void run() throws Exception {
        if (StringUtils.isBlank(editor)) {
            printDefaultEditor();
        } else {
            setDefaultEditor(editor);
        }
    }

    private void printDefaultEditor() {
        String defEditor = prop.getProperty("EDITOR");
        if (!StringUtils.isBlank(defEditor)) {
            int i = defEditor.indexOf(" --");
            if (i > 0) {
                defEditor = defEditor.substring(0, i);
            }
            System.out.println("[i] Default editor in global config: " + new File(defEditor).getName());
            System.out.println("[i] Editor Path: " + defEditor);
        } else {
            System.out.println("[i] There is no defined default editor in global config.");
        }
    }

    private void setDefaultEditor(String newEditor) throws Exception {
        String defEditor = prop.getProperty("EDITOR");
        if (!StringUtils.isBlank(defEditor)) {
            System.out.println("[i] There is already defined default editor in global config: " + defEditor);
            if (doesUserConfirm()) {
                setNewEditor(newEditor);
            } else {
                System.out.println("[✓] Ok, default editor is not modified.");
            }
        } else {
            setNewEditor(newEditor);
        }
    }

    private boolean doesUserConfirm() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("[?] Are you sure to set default editor? (y/n) ");
        String answer = scanner.next();
        return !StringUtils.isBlank(answer) && (answer.charAt(0) == 'Y' || answer.charAt(0) == 'y');
    }

    private void setNewEditor(String newEditor) throws Exception {
        String editorFilePath = getEditorFilePath(newEditor.trim());
        if (isEditorAlive(editorFilePath)) {
            editorFilePath += getWaitOptionByEditor(editorFilePath);
            prop.setProperty("EDITOR", editorFilePath);
            System.out.println("[✓] Default editor is set as: " + prop.getProperty("EDITOR"));
        }
    }

    private String getWaitOptionByEditor(String editorFilePath) {
        System.out.println("[*] Checking whether editor has --wait option?");
        if (editorFilePath.endsWith("/brackets")) {
            System.out.println("[✓] Has --wait option, will be used.");
            return " --wait-apps";
        }
        // code, sublime, atom, nano, micro
        Command cmd = new Command(editorFilePath + " --help");
        System.out.println("[*] " + cmd.getCommandString());
        if (cmd.execute()) {
            String[] helpLines = cmd.toString().toLowerCase().split("\\r?\\n");
            if (helpLines.length > 0) {
                helpLines[0] = helpLines[0].toLowerCase();
                if (helpLines[0].contains("sublime text")
                    || helpLines[0].contains("visual studio code")
                    || helpLines[0].contains("atom editor")) {
                    System.out.println("[✓] Has --wait option, will be used.");
                    return " --wait";
                }
            }
            System.out.println("[✓] Nope, no need --wait option.");
            return "";
        }
        System.out.println("[w] Could not be detected, but not a big problem.");
        return "";
    }

    private String getEditorFilePath(String editor) throws Exception {
        Command cmd = new Command(new String[]{"which", editor});
        if (cmd.execute() && !StringUtils.isBlank(cmd.toString())) {
            return cmd.toString();
        } else {
            throw new Exception("Ooops! Could not find editor " + editor + " in global path! \n"
                + "Failed: " + cmd.getCommandString());
        }
    }

    private boolean isEditorAlive(String editor) throws IOException {
        Process process = Runtime.getRuntime().exec(editor);
        if (!process.isAlive()) {
            System.out.println("[e] Editor could not be triggered. Please check whether command is executable and correct.");
            System.out.println("[i] Editor path: " + editor);
            return false;
        } else {
            process.destroy();
            return true;
        }
    }
}
