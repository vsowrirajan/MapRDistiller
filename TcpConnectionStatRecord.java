import java.io.File;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

public class TcpConnectionStatRecord extends Record {
	long localIp, remoteIp, rxQ, txQ;
	int localPort, remotePort, pid;
	
	TcpConnectionStatRecord(){}
	public String toString(){
		return super.toString() + " tcp.connection.stats " + longIpToString(localIp) + ":" + localPort + " " + longIpToString(remoteIp) + ":" + remotePort + 
				" txQ:" + txQ + " rxQ:" + rxQ + " pid:" + pid;
	}
	public static String longIpToString(long ip){
		String ipStr = 	String.valueOf(ip % 256) + "." + 
						String.valueOf(ip / 256 % 256) + "." + 
						String.valueOf(ip / 65536 % 256) + "." + 
						String.valueOf(ip / 16777216);
		return ipStr;
	}
	public static boolean produceRecords(RandomAccessFile proc_net_tcp, SubscriptionRecordQueue outputQueue){
		long startTime = System.currentTimeMillis();
		String line = null;
		String socketId;
		int processesChecked=0, fdChecked=0, recordsGenerated=0;
		TcpConnectionStatRecord newrecord;
		HashMap<String,TcpConnectionStatRecord> recordMap = new HashMap<String,TcpConnectionStatRecord>(32000);
		try{
			proc_net_tcp.seek(0l);
			
			if((line = proc_net_tcp.readLine()) != null ){
				while( (line = proc_net_tcp.readLine()) != null ) {
					String parts[] = line.trim().split("\\s+");
					if(parts[3].equals("01")){
						newrecord = new TcpConnectionStatRecord();
						newrecord.localIp = Long.parseLong(parts[1].split(":")[0], 16);
						newrecord.localPort = Integer.parseInt(parts[1].split(":")[1], 16);
						newrecord.remoteIp = Long.parseLong(parts[2].split(":")[0], 16);
						newrecord.remotePort = Integer.parseInt(parts[2].split(":")[1], 16);
						newrecord.rxQ = Integer.parseInt(parts[4].split(":")[1], 16);
						newrecord.txQ = Integer.parseInt(parts[4].split(":")[0], 16);
						socketId = parts[9];
						recordMap.put(socketId, newrecord);
					}
				}
			}
		} catch (Exception e){
			System.err.println("Failed to parse line: " + line);
			e.printStackTrace();
			return false;
		}
		

		FilenameFilter fnFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if(name.charAt(0) >= '1' && name.charAt(0) <= '9'){
					return true;
				}
				return false;
			}
		};
		File ppFile = new File("/proc");
		File[] pPaths = ppFile.listFiles(fnFilter);
		if(pPaths == null) return false;
		processesChecked = pPaths.length;
		long timestamp = System.currentTimeMillis();
		
		//For each process in /proc
		for (int pn = 0; pn<pPaths.length; pn++){
			int pid = Integer.parseInt(pPaths[pn].getName());
			String fdPathStr = pPaths[pn].toString() + "/fd";
			File fdFile = new File(fdPathStr);
			File[] fdPaths = fdFile.listFiles(fnFilter);
			if(fdPaths != null) {
				//For each file descriptor in /proc/[pid]/fd
				fdChecked += fdPaths.length;
				for (int x=0; x<fdPaths.length; x++){
					try{
						String linkTarget = Files.readSymbolicLink(Paths.get(fdPaths[x].toString())).toString();
						if(linkTarget.startsWith("socket:[")){
							socketId = linkTarget.split("\\[")[1].split("\\]")[0];
							if( (newrecord = recordMap.get(socketId)) != null ){
								newrecord.pid = pid;
								newrecord.timestamp = timestamp;
								if(!outputQueue.put(newrecord)){
									return false;
								}
								recordsGenerated++;
							}
							//System.err.println("pid: " + pid + " socketId: " + socketId + " path: " + fdPaths[x].toString());
						}
					} catch (Exception e){}
				}
			}
		}
		long duration = System.currentTimeMillis() - startTime;
		//System.err.println("Generated " + recordsGenerated + " output records from " + processesChecked + " processes and " + 
		//					fdChecked + " file descriptors in " + duration + " ms");
		
		return true;
	}
}
