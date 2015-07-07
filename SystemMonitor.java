import java.util.Map;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.apache.xmlrpc.webserver.WebServer;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.XmlRpcException;

public class SystemMonitor {
	
	private static ConcurrentHashMap<String, RecordQueue> n2rqm;
	
	public static class Receiver {
		public String sendCommand(String str){
			System.err.println("Got: " + str);
			return "yaba";
		}
	};
	
	private final WebServer webServer;
	private final XmlRpcServer xmlRpcServer;
	public SystemMonitor(int port ){
		webServer = new WebServer(port);
		xmlRpcServer = webServer.getXmlRpcServer();
		PropertyHandlerMapping phm = new PropertyHandlerMapping();
		
		try{
			phm.addHandler("du", Receiver.class);
		} catch (XmlRpcException e) {
			System.err.println("Failed to setup RPC server");
			e.printStackTrace();
			System.exit(1);
		}
		
		xmlRpcServer.setHandlerMapping(phm);
		XmlRpcServerConfigImpl serverConfig = (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();
		serverConfig.setEnabledForExceptions(true);
		serverConfig.setContentLengthOptional(false);
		try{
			webServer.start();
		} catch (Exception e) {
			System.err.println("Failed to start RPC server");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private static ConcurrentHashMap<String, RecordQueue> nameToRecordQueueMap;
	public static void main(String[] args) {
		ConcurrentHashMap<String, Integer> rawMetricList = new ConcurrentHashMap<String, Integer>(1000);
		rawMetricList.put("cpu.system", 1000);
		rawMetricList.put("disk.system",  1000);
		rawMetricList.put("memory.system",  1000);
		rawMetricList.put("network.interfaces",  1000);
		rawMetricList.put("process.resources",  1000);
		rawMetricList.put("thread.resources", 1000);
		rawMetricList.put("tcp.connection.stats", 1000);
		boolean shouldExit = false;
		nameToRecordQueueMap = new ConcurrentHashMap<String, RecordQueue>(1000);
		n2rqm = nameToRecordQueueMap;
		
		//Generate the configuration that decides how SystemMonitor will run/what it will do
		Properties configuration = DefaultConfiguration.getConfiguration();
		
		for (int argc = 0; argc<args.length; argc++) {
			String[] substrings = args[argc].split(":", 2);
			if(substrings.length > 1 && substrings[0].equals("conf") && substrings[1].length() > 0) {
				configuration = DefaultConfiguration.applyConfigurationFile(configuration, substrings[1]);
				
			} else {
				System.err.println("Unknown command line option: " + args[argc]);
				System.exit(1);
			}
		}
		
		
		//Configuration done, implement monitoring defined by configuration.
		
		//Start the RPC server for client requests
		SystemMonitor myRpcServer = new SystemMonitor(Integer.parseInt(configuration.getProperty("rpc.server.port", "23721")));
		
		//Eventually, this next step should be to dynamically create Monitor's based on a configuration file.  For now, there is a set of diagnostics that are statically defined and must be referenced by their given configuration properties.
		//Start iostatProcessRecordProducer if enabled
		
		ProcRecordProducer procRecordProducer = new ProcRecordProducer(nameToRecordQueueMap, rawMetricList);
		procRecordProducer.start();
		
		//Monitors are started, now wait until its time to shut down.
		long lastStatus=0l, statusInterval = 10000l;
		while (!shouldExit) {
			if(System.currentTimeMillis() - statusInterval > lastStatus) {
				lastStatus = System.currentTimeMillis();
				Iterator<Map.Entry<String, RecordQueue>>it = nameToRecordQueueMap.entrySet().iterator();
				while(it.hasNext()) {
					Map.Entry<String, RecordQueue> pair = (Map.Entry<String, RecordQueue>)it.next();
					RecordQueue q = (RecordQueue) pair.getValue();
					System.err.println("RecordQueue " + pair.getKey() + " contains " + q.queueSize() + " records");
					//System.err.println(q.printNewestRecords(5));
				}
			}
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
				if(!shouldExit) {
					System.err.println("SystemMonitor shutting down due to exception");
					e.printStackTrace();
					shouldExit = true;
				}
				System.err.println("SystemMonitor shutting down by request");
			}
		}
		
		
		/**
		//Its time to shut down, inform all Monitors.
		if (iostatProcessRecordProducer != null && iostatProcessRecordProducer.isAlive()) {
			iostatProcessRecordProducer.exitRequest();
		}
		
		
		
		//Wait for all Monitors to shut down, interrupt them if this shutdown thread is interrupted.
		while(iostatProcessRecordProducer.isAlive()) {
			try {
				Thread.sleep(1000);
			} catch (Exception E) {
				System.err.println("SystemMonitor interrupted during shutdown, interrupting child thread iostatProcessRecordProducer");
				iostatProcessRecordProducer.interrupt();
			}
		}
		**/
		
		//All Monitors spawned by this SystemMonitor are stopped.
		return;
	}

}