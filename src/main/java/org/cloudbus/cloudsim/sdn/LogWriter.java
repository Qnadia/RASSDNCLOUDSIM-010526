/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

 package org.cloudbus.cloudsim.sdn;

 import java.io.File;
 import java.io.FileNotFoundException;
 import java.io.FileOutputStream;
 import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
 
 public class LogWriter {
	 private PrintStream out = null;
 
	 private static HashMap<String, LogWriter> map = new HashMap<String, LogWriter>();
	  private static Map<String, List<String>> bufferMap = new HashMap<>(); // Buffer pour stocker les logs

 
	 private LogWriter(String name) {
		//  if (name != null) {
		// 	 out = openfile(name);
		//  } else {
		// 	 // Utiliser System.out si le nom est null
		// 	 out = System.out;
		// 	 System.err.println("LogWriter: Utilisation de System.out car le nom du fichier est null.");
		//  }
	 }
	 
	 /* MAJ Nadia */
	 public static LogWriter getLogger(String name) {
        String exName = Configuration.workingDirectory + Configuration.experimentName + name;
        return map.computeIfAbsent(exName, k -> {
            System.out.println("Logger registered for: " + exName);
            bufferMap.put(exName, new ArrayList<>()); // Initialiser le buffer
            return new LogWriter(exName);
        });
    }

	public void print(String s) {
        bufferMap.get(getKey()).add(s);
    }

    public void printLine(String s) {
        bufferMap.get(getKey()).add(s + "\n");
    }

	//  public static LogWriter getLogger(String name) {
	// 	 if (name == null) {
	// 		 System.err.println("LogWriter.getLogger: Le nom fourni est null. Utilisation de System.out.");
	// 		 return new LogWriter(null);
	// 	 }
 
	// 	 String workingDir = Configuration.workingDirectory;
	// 	 if (!workingDir.endsWith("/") && !workingDir.endsWith("\\")) {
	// 		 workingDir += System.getProperty("file.separator");
	// 	 }
 
	// 	 String experimentName = Configuration.experimentName;
	// 	 if (!experimentName.endsWith("/") && !experimentName.endsWith("\\")) {
	// 		 experimentName += System.getProperty("file.separator");
	// 	 }
 
	// 	 String exName = workingDir + experimentName + name;
	// 	 LogWriter writer = map.get(exName);
	// 	 if (writer != null)
	// 		 return writer;
 
	// 	 System.out.println("Creating logger..:" + exName);
	// 	 writer = new LogWriter(exName);
	// 	 map.put(exName, writer);
	// 	 return writer;
	//  }
 
	//  public void print(String s) {
	// 	 if (out != null) {
	// 		 out.print(s);
	// 	 } else {
	// 		 System.err.println("LogWriter: " + s);
	// 	 }
	//  }
 
	//  public void printLine() {
	// 	 if (out != null) {
	// 		 out.println();
	// 	 } else {
	// 		 System.err.println();
	// 	 }
	//  }
 
	//  public void printLine(String s) {
	// 	 if (out != null) {
	// 		 out.println(s);
	// 	 } else {
	// 		 System.err.println("LogWriter: " + s);
	// 	 }
	//  }

	public static void flushAll() {
        map.forEach((name, writer) -> {
            try {
                File file = new File(name);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                
                try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
                    bufferMap.get(name).forEach(out::print);
                }
                System.out.println("Logs written to: " + name);
            } catch (FileNotFoundException e) {
                System.err.println("Error writing log: " + name);
                e.printStackTrace();
            }
        });
        bufferMap.clear();
        map.clear();
    }

    private String getKey() {
        return map.entrySet().stream()
            .filter(entry -> entry.getValue() == this)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }
 
	 private PrintStream openfile(String name) {
		 PrintStream out = null;
		 try {
			 File file = new File(name);
			 File parent = file.getParentFile();
			 if (parent != null && !parent.exists()) {
				 boolean dirsCreated = parent.mkdirs();
				 if (!dirsCreated) {
					 System.err.println("LogWriter: Impossible de créer les répertoires parents pour " + name);
				 }
			 }
			 out = new PrintStream(new FileOutputStream(file, true)); // Mode append
			 System.out.println("LogWriter: Fichier de log ouvert: " + name);
		 } catch (FileNotFoundException e) {
			 e.printStackTrace();
			 // Fallback vers System.out pour éviter NullPointerException
			 System.err.println("LogWriter: Impossible d'ouvrir le fichier de log '" + name + "'. Utilisation de System.out comme fallback.");
			 out = System.out;
		 }
		 return out;
	 }
 
	 public static String getExtension(String fullPath) {
		 int dot = fullPath.lastIndexOf(".");
		 if (dot == -1 || dot == fullPath.length() - 1) {
			 return "";
		 }
		 return fullPath.substring(dot + 1);
	 }
 
	 public static String getBaseName(String fullPath) { // gets filename without extension
		 int dot = fullPath.lastIndexOf(".");
		 int sep = Math.max(fullPath.lastIndexOf("/"), fullPath.lastIndexOf("\\"));
		 if (dot == -1) {
			 return fullPath.substring(sep + 1);
		 }
		 return fullPath.substring(sep + 1, dot);
	 }
 
	 public static String getPath(String fullPath) {
		 int sep = Math.max(fullPath.lastIndexOf("/"), fullPath.lastIndexOf("\\"));
		 if (sep == -1) {
			 return "";
		 }
		 return fullPath.substring(0, sep + 1); // Inclure le séparateur
	 }
 }
 

// import java.io.IOException;
// import java.io.PrintStream;
// import java.util.HashMap;

// public class LogWriter {
// 	private PrintStream out = null;

// 	private static HashMap<String,LogWriter> map = new HashMap<String,LogWriter>();
	
// 	private LogWriter(String name) {
// 		out = openfile(name);
// 	}
	
// 	public static LogWriter getLogger(String name) {
// 		String exName = Configuration.workingDirectory+Configuration.experimentName+name;
// 		LogWriter writer = map.get(exName);
// 		if(writer != null)
// 			return writer;
		
// 		System.out.println("Creating logger..:" +exName);
// 		writer = new LogWriter(exName);
// 		map.put(exName, writer);
// 		return writer;
// 	}

// 	public void print(String s) {
// 		if(out == null)
// 			System.err.println("WorkloadResultWriter: "+s);
// 		else
// 			out.print(s);
// 	}
	
// 	public void printLine() {
// 		if(out == null)
// 			System.err.println("");
// 		else
// 			out.println();
// 	}
	
// 	public void printLine(String s) {
// 		out.println(s);
// 	}
		
// 	private PrintStream openfile(String name) {
// 		PrintStream out = null;
// 		try {
// 			out = new PrintStream(name);
// 		} catch (IOException e) {
// 			e.printStackTrace();
// 		}
// 		return out;
// 	}
	
// 	 public static String getExtension(String fullPath) {
// 	    int dot = fullPath.lastIndexOf(".");
// 	    return fullPath.substring(dot + 1);
// 	  }

// 	  public static String getBaseName(String fullPath) { // gets filename without extension
// 	    int dot = fullPath.lastIndexOf(".");
// 	    int sep = fullPath.lastIndexOf("/");
// 	    return fullPath.substring(sep + 1, dot);
// 	  }

// 	  public static String getPath(String fullPath) {
// 	    int sep = fullPath.lastIndexOf("/");
// 	    return fullPath.substring(0, sep);
// 	  }
// }
