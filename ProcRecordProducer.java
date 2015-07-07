import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.TreeSet;
import java.io.File;
import java.math.BigInteger;
import java.net.NetworkInterface;
import java.lang.Integer;
import java.io.FilenameFilter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ProcRecordProducer extends Thread {
	boolean shouldExit=false;
	TreeSet<GatherMetricEvent> metricSchedule = new TreeSet<GatherMetricEvent>(new GMEComparator());
	
	//The number of clock ticks (jiffies) per second.  Typically 100 but custom kernels can be compiled otherwise
	int clockTick;
	//The length of time, in milliseconds, represented by a single clock tick (jiffy). Typically 10ms
	Double clockTickms;
	ConcurrentHashMap<String, RecordQueue> nameToRecordQueueMap;
	long GATHER_METRIC_RETRY_INTERVAL = 1000l;
	int marker_cpu_system=0, marker_cpu_system_=1;
	
	//RandomAccessFiles that we only need to open once, and so can be created at the class level
	RandomAccessFile proc_stat = null, proc_diskstats = null, proc_vmstat = null, proc_meminfo = null, proc_net_tcp = null;
	
	//RecordQueues to hold output records for all the different types of metrics this RecordProducer can produce
	SubscriptionRecordQueue cpu_system = null, 			//Queue for "cpu.system" raw metrics, sourced from /proc/stat
							disk_system = null, 		//Queue for "disk.system" raw metrics, sourced from /proc/diskstats
							memory_system = null, 		//Queue for "memory.system" raw metrics, sourced from /proc/vmstat and /proc/meminfo
							network_interfaces = null,	//Queue for "network.interfaces" raw metrics, sourced from /sys/class/net/<iface>
							process_resources = null,	//Queue for "process.resources" raw metrics, sourced from /proc/[pid]/stat
							thread_resources = null,	//Queue for "thread.resources" raw metrics, sourced from /proc/[pid]/task/[tid]/stat
							tcp_connection_stats = null;//Queue for "tcp.connection.stats" raw metrics, sourced from /proc/net/tcp and /proc/[pid]/fd/*

	//HashMaps that map unique ID strings to specific Records that hold the counters to use for differential comparisons
	//For network interfaces, each record represents a nework interface
	//For system disks, each record represents a disk
	//For process resources, each record represents a process/thread
	ConcurrentHashMap<String, NetworkInterfaceRecord> networkInterfaceCounterMap = null;
	ConcurrentHashMap<String, DiskstatRecord> systemDiskCounterMap = null;
	ConcurrentHashMap<String, ProcessResourceRecord> processResourceCounterMap = null; 
	ConcurrentHashMap<String, ThreadResourceRecord> threadResourceCounterMap = null;
	
	//Counters for the most recent sample of system CPU/memory to be used for differential values when the metric is next collected
	SystemCpuRecord systemCpuCounters = new SystemCpuRecord();
	SystemMemoryRecord systemMemoryCounters = null;
	
	ProcRecordProducer(ConcurrentHashMap<String, RecordQueue> nameToRecordQueueMap, ConcurrentHashMap<String, Integer> rawMetricList) {
		//setClockTick();
		this.nameToRecordQueueMap = nameToRecordQueueMap;
		Iterator<Map.Entry<String, Integer>> i = rawMetricList.entrySet().iterator();
		if(!i.hasNext()){
			System.err.println("No metrics requested");
			System.exit(1);;
		}
		while(i.hasNext()) {
			Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>)i.next();
			String metricName = pair.getKey();
			int periodicity = pair.getValue().intValue();
			
			if(metricName.equals("cpu.system")){
				if(!initialize_cpu_system(periodicity)){
					System.err.println("Failed to initialize cpu.system metric");
					System.exit(1);
				}
			} else if (metricName.equals("disk.system")){
				if(!initialize_disk(periodicity)) {
					System.err.println("Failed to initialize disk.system metric");
					System.exit(1);
				}
			} else if (metricName.equals("memory.system")){
				if(!initialize_memory_system(periodicity)) {
					System.err.println("Failed to initialize memory.system metric");
					System.exit(1);
				}
			} else if (metricName.equals("network.interfaces")) {
				if(!initialize_network_interfaces(periodicity)) {
					System.err.println("Failed to initialize network.interfaces metric");
					System.exit(1);
				}
			} else if (metricName.equals("process.resources")) {
				if(!initialize_process_resources(periodicity)) {
					System.err.println("Failed to initialize process.resources metric");
					System.exit(1);
				}
			} else if (metricName.equals("thread.resources")) {
				if(!initialize_thread_resources(periodicity)) {
					System.err.println("Failed to initialize process.resources metric");
					System.exit(1);
				}
			} else if (metricName.equals("tcp.connection.stats")) {
				if(!initialize_tcp_connection_stats(periodicity)) {
					System.err.println("Failed to initialize process.resources metric");
					System.exit(1);
				}
			} else {
				System.err.println("Request received to gather an unknown metric: " + metricName);
				System.exit(1);
			}
			System.err.println("Received request to gather metric " + metricName + " with periodicity " + periodicity + " ms");
		}
		
	}
	public void run() {
		long timeSpentCollectingMetrics=0l;
		long tStartTime = System.currentTimeMillis();
		
		if(!setupProcFiles()){
			System.err.println("Failed to setup /proc files");
			System.exit(1);
		}
		//Keep trying to generate requested metrics until explicitly requested to exit
		while(!shouldExit){
			//Read the next scheduled event from the schedule;
			GatherMetricEvent event = metricSchedule.first();
			
			//Check how long until the event is supposed to be executed
			//The time member in the GatherMetricEvent indicates the time at which the metric was last gathered
			//The periodicity member indicates how long we try to wait (in ms) in between rounds of gathering the metric
			//The time the metric should be gathered next is equal to the time the metric was last gathered (event.time) plus the 
			//the periodicity.  Therefore the time we need to sleep can be calcaulted by substracting the time now from that target time
			long actionStartTime = System.currentTimeMillis();
			long sleepTime = event.targetTime - actionStartTime;
			
			//If the time until the event should be executed is greater than 1 second from now, then sleep for a second and check again
			//TThis is useful when new metrics need to be gathered.  A new metric that needs to be gathered will have it's first sample
			//gathered with a delay of 1 second at most.
			//In contrast, if we didn't do this, if we were gathering metricA on a 5 second period, and right after we gathered metricA, we registered
			//metricB with 1 second period, then metricB wouldn't get serviced until about 5 seconds, after we service metricA.  
			if(sleepTime > 1000) {
				try{
					Thread.sleep(1000);
				} catch (Exception e) {
					System.err.println("interrupted while sleeping in ProcRecordProducer");
					e.printStackTrace();
				}
			//If the time until the metric is supposed to gathered is <=1 second
			} else {			
				//Remove the metric event from the schedule since we will re-add it with an updated time once it's been gathered this iteration
				metricSchedule.remove(event);
				
				//Sleep until it's time to gather the metric
				if(sleepTime > 0l){
					try{
						Thread.sleep(sleepTime);
					} catch (Exception e) {
						System.err.println("interrupted while sleeping in ProcRecordProducer");
						e.printStackTrace();
					}
				}
				
				actionStartTime = System.currentTimeMillis();
				//Gather the specific metric type
				
				/**
				 * Eventually, in this section of code, we should group together metrics that are gathered with periodicities that are
				 * identical or factors of each other.  E.g. if cpu.system is to be gathered once a second and process.resources to be
				 * gathered once every two seconds, we should schedule them such that at t=1 we gather cpu.system, at t=2 we gather
				 * cpu.system and cpu.resources
				 */
				if(event.metricName.equals("cpu.system")){	
					//If we successfully gather the record
					if(generateSystemCpuRecord(event.previousTime, actionStartTime)){
						//Update the event with the time we gathered the metric and the new target time
						event.previousTime = actionStartTime;
						event.targetTime = actionStartTime + event.periodicity;
					//If we failed to gather the record
					} else {
						//Schedule another attempt to gather the metric after the GATHER_METRIC_RETRY_INTERVAL
						System.err.println("Failed to generate cpu.system metric");
						event.targetTime = actionStartTime + GATHER_METRIC_RETRY_INTERVAL;
					}
					metricSchedule.add(event);
				} else if (event.metricName.equals("disk.system")) {
					if(generateDiskRecord(event.previousTime, actionStartTime)){
						event.previousTime = actionStartTime;
						event.targetTime = actionStartTime + event.periodicity;
					} else {
						System.err.println("Failed to generate disk.system metric");
						event.targetTime = actionStartTime + GATHER_METRIC_RETRY_INTERVAL;
					}
					metricSchedule.add(event);
				} else if (event.metricName.equals("memory.system")) {
					if(generateSystemMemoryRecord(event.previousTime, actionStartTime)){
						event.previousTime = actionStartTime;
						event.targetTime = actionStartTime + event.periodicity;
					} else {
						System.err.println("Failed to generate memory.system metric");
						event.targetTime = actionStartTime + GATHER_METRIC_RETRY_INTERVAL;
					}
					metricSchedule.add(event);
				} else if (event.metricName.equals("network.interfaces")) {
					if(generateNetworkInterfaceRecords(event.previousTime, actionStartTime)){
						event.previousTime = actionStartTime;
						event.targetTime = actionStartTime + event.periodicity;
					} else {
						System.err.println("Failed to generate network.interfaces metric");
						event.targetTime = actionStartTime + GATHER_METRIC_RETRY_INTERVAL;
					}
					metricSchedule.add(event);
				} else if (event.metricName.equals("process.resources")) {
					if(generateProcessResourceRecords(event.previousTime, actionStartTime)){
						event.previousTime = actionStartTime;
						event.targetTime = actionStartTime + event.periodicity;
					} else {
						System.err.println("Failed to generate process.resources metric");
						event.targetTime = actionStartTime + GATHER_METRIC_RETRY_INTERVAL;
					}
					metricSchedule.add(event);
				} else if (event.metricName.equals("thread.resources")) {
					if(generateThreadResourceRecords(event.previousTime, actionStartTime)){
						event.previousTime = actionStartTime;
						event.targetTime = actionStartTime + event.periodicity;
					} else {
						System.err.println("Failed to generate thread.resources metric");
						event.targetTime = actionStartTime + GATHER_METRIC_RETRY_INTERVAL;
					}
					metricSchedule.add(event);
				} else if (event.metricName.equals("tcp.connection.stats")) {
					if(generateTcpConnectionStatRecords(event.previousTime, actionStartTime)){
						event.previousTime = actionStartTime;
						event.targetTime = actionStartTime + event.periodicity;
					} else {
						System.err.println("Failed to generate tcp.connection.stats metric");
						event.targetTime = actionStartTime + GATHER_METRIC_RETRY_INTERVAL;
					}
					metricSchedule.add(event);
				} else {
					System.err.println("Request to gather an unknown metric type: " + event.metricName);
				}
				timeSpentCollectingMetrics += System.currentTimeMillis() - actionStartTime;
				long elapsedTime = System.currentTimeMillis() - tStartTime;
				System.err.println("Running for " + timeSpentCollectingMetrics + " ms out of " + elapsedTime + " ms, " + (100 * timeSpentCollectingMetrics / elapsedTime));
				if(elapsedTime > 60000l) {
					timeSpentCollectingMetrics=0l;
					tStartTime = System.currentTimeMillis();
				}
			}
		}
		
		//Time to shut down
		cpu_system.unregisterProducer("/proc");
		closeProcFiles();
	}
	public boolean setupProcFiles(){
		proc_diskstats = setupFile("/proc/diskstats");
		proc_stat = setupFile("/proc/stat");
		proc_vmstat = setupFile("/proc/vmstat");
		proc_meminfo = setupFile("/proc/meminfo");
		proc_net_tcp = setupFile("/proc/net/tcp");
		if(	proc_diskstats == null ||
			proc_stat == null ||
			proc_vmstat == null ||
			proc_meminfo == null ||
			proc_net_tcp == null )
			return false;
		return true;
	}
	public RandomAccessFile setupFile(String filePath){
		try {
			RandomAccessFile rafFile = new RandomAccessFile(filePath, "r");
			return rafFile;
		} catch (Exception e) {
			System.err.println("Failed to open " + filePath);
			e.printStackTrace();
			return null;
		}
	}
	public void closeProcFiles(){
		closeFile(proc_diskstats);
		closeFile(proc_stat);
		closeFile(proc_vmstat);
		closeFile(proc_meminfo);
		closeFile(proc_net_tcp);
	}
	public void closeFile(RandomAccessFile rafFile){
		try {
			rafFile.close();
		} catch (Exception e) {
			System.err.println("Failed to close " + rafFile);
			e.printStackTrace();
		}
	}
	public String readStringFromFile(String path) {
		RandomAccessFile file;
		try {
			file = new RandomAccessFile(path, "r");
			String line = file.readLine();
			file.close();
			if(line != null){
				return line.trim().split("\\s+")[0];
			}
		} catch (Exception e) {
			System.err.println("Caught an exception while reading String from file " + path);
			e.printStackTrace();
		}
		return null;
	}
	public BigInteger readBigIntegerFromFile(String path) {
		RandomAccessFile file;
		try {
			file = new RandomAccessFile(path, "r");
			String line = file.readLine();
			file.close();
			if(line != null){
				return new BigInteger(line.trim().split("\\s+")[0]);
			}
		} catch (Exception e) {
			System.err.println("Caught an exception while reading BigInteger file " + path);
			e.printStackTrace();
		}
		return null;
	}
	public int readIntFromFile(String path) {
		RandomAccessFile file;
		try {
			file = new RandomAccessFile(path, "r");
			String line = file.readLine();
			file.close();
			if(line != null){
				String part = line.trim().split("\\s+")[0];
				int ret = Integer.parseInt(part);
				return ret;
			}
		} catch (Exception e) {
			System.err.println("Caught an exception while reading BigInteger file " + path);
			e.printStackTrace();
		}
		return -1;
	}
	private boolean initialize_thread_resources(int periodicity){
		threadResourceCounterMap = new ConcurrentHashMap<String, ThreadResourceRecord>(65536);
		thread_resources = new SubscriptionRecordQueue("thread.resources", 131072);
		thread_resources.registerProducer("/proc");
		GatherMetricEvent event = new GatherMetricEvent(0l, 0l, "thread.resources", periodicity);
		metricSchedule.add(event);
		nameToRecordQueueMap.put("thread.resources",  thread_resources);
		return true;
	}
	private boolean generateThreadResourceRecords(long previousTime, long now){
		long st = System.currentTimeMillis();
		int outputRecordsGenerated=0;
		try {
			FilenameFilter fnFilter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					if(name.charAt(0) >= '1' && name.charAt(0) <= '9')
						return true;
					return false;
				}
			};
			File ppFile = new File("/proc");
			File[] pPaths = ppFile.listFiles(fnFilter);
			//For each process in /proc
			for (int pn = 0; pn<pPaths.length; pn++){
				int ppid = Integer.parseInt(pPaths[pn].getName());
				String taskPathStr = pPaths[pn].toString() + "/task";
				
				File tpFile = new File(taskPathStr);
				File[] tPaths = tpFile.listFiles(fnFilter);
				if(tPaths != null) {
					for (int x=0; x<tPaths.length; x++){
						String statPath = tPaths[x].toString() + "/stat";
						ThreadResourceRecord threadRecord = ThreadResourceRecord.produceRecord(statPath, ppid);
						if(threadRecord != null){
							threadRecord.clockTick = clockTick;
							thread_resources.put(threadRecord);
							outputRecordsGenerated++;
						}
					}
				}
			}
		} catch(Exception e){e.printStackTrace();}	
		long dur = System.currentTimeMillis() - st;
		//System.err.println("Generated " + outputRecordsGenerated + " thread output records in " + dur + " ms, " + ((double)outputRecordsGenerated / (double)dur));
		return true;			
	}
	private boolean generateDifferentialThreadResourceRecords(long previousTime, long now){
		long st = System.currentTimeMillis();
		int outputRecordsGenerated=0, counterRecordsGenerated=0;
		try {
			FilenameFilter fnFilter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					if(name.charAt(0) >= '1' && name.charAt(0) <= '9')
						return true;
					return false;
				}
			};
			File ppFile = new File("/proc");
			File[] pPaths = ppFile.listFiles(fnFilter);
			
			//For each process in /proc
			for (int pn = 0; pn<pPaths.length; pn++){
				int ppid = Integer.parseInt(pPaths[pn].getName());
				String taskPathStr = pPaths[pn].toString() + "/task";
				
				File tpFile = new File(taskPathStr);
				File[] tPaths = tpFile.listFiles(fnFilter);
				if(tPaths != null) {
					for (int x=0; x<tPaths.length; x++){
						String statPath = tPaths[x].toString() + "/stat";
						ThreadResourceRecord threadRecord = ThreadResourceRecord.produceRecord(statPath, ppid);
						if(threadRecord == null){
							continue;
						}
						
					/**
					 * 
					 * Here, we can generate a differential between counters we gathered last time around vs.
					 * this time around, or we could do this so we just write the straight counters to the output queue.
					 * 
					 * Pushing counters to the output queue has the benefit of being useful for calculating CPU util over a period of time.
					 * If you gather 1 second, differential records, then to get a 10 second moving average, you have to add the cpu usage
					 * jiffes from each of 10 1-second samples every second (not to mention needing to calculate the counter diffs in each
					 * of the 1 second samples against the previous 1 second sample.
					 * 
					 * However, if you push the counters, and you want a 10 second moving average, you would just need to find the cpu usage
					 * counters from the 10 second ago sample and diff those once against the current counters.  It's many fewer calculations.
					 * 
					 * On the flip side, by pushing counters, every time you wan to look at a chart of 1-second interval CPU usage, you 
					 * have to do the differential calculations on each sample.
					 * 
					 * We could store both, differentials and counters in a single record, but that will also consume more memory.
					 * Design here is obviously not quite finished... not sure what the best approach is yet.
					 * 
					 */
						//Check whether we saw this thread the last time we gathered process_resources metrics
						if(threadResourceCounterMap.containsKey(tPaths[x].getName())){
							//If we've seen this thread before, then diff the counters and put the diff record in the output queue
							thread_resources.put(ThreadResourceRecord.diff(threadResourceCounterMap.get(tPaths[x].getName()), threadRecord, clockTick));
							outputRecordsGenerated++;
						}
							
						//Record the thread counters so they can be used for differential stats the next time through
						threadResourceCounterMap.put(tPaths[x].getName(), threadRecord);
						counterRecordsGenerated++;
					}
				}
			}
			Iterator<Map.Entry<String, ThreadResourceRecord>>i = threadResourceCounterMap.entrySet().iterator();
			while(i.hasNext()) {
				Map.Entry<String, ThreadResourceRecord> pair = (Map.Entry<String, ThreadResourceRecord>)i.next();
				if(pair.getValue().timestamp < now){
					threadResourceCounterMap.remove(pair.getKey());
				}
			}
		} catch(Exception e){}	

		long dur = System.currentTimeMillis() - st;
		System.err.println("Generated " + outputRecordsGenerated + " thread output records and " + counterRecordsGenerated + " counter records" + " in " + dur + " ms, " + ((double)outputRecordsGenerated / (double)dur));
		return true;
					
	}
	private boolean initialize_process_resources(int periodicity){
		processResourceCounterMap = new ConcurrentHashMap<String, ProcessResourceRecord>(65536);
		process_resources = new SubscriptionRecordQueue("process.resources", 131072);
		process_resources.registerProducer("/proc");
		GatherMetricEvent event = new GatherMetricEvent(0l, 0l, "process.resources", periodicity);
		metricSchedule.add(event);
		nameToRecordQueueMap.put("process.resources",  process_resources);
		return true;
	}
	private boolean generateProcessResourceRecords(long previousTime, long now) {
		long st = System.currentTimeMillis();
		int outputRecordsGenerated=0, counterRecordsGenerated=0;
		
		try {
			FilenameFilter fnFilter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					if(name.charAt(0) >= '1' && name.charAt(0) <= '9')
						return true;
					return false;
				}
			};
			File ppFile = new File("/proc");
			File[] pPaths = ppFile.listFiles(fnFilter);
			
			//For each process in /proc
			for (int pn = 0; pn<pPaths.length; pn++){
				//Retrieve the process counters contained in /proc/[pid]/stat
				ProcessResourceRecord processRecord = ProcessResourceRecord.produceRecord(pPaths[pn].toString() + "/stat");
				//Move on to the next process if we failed to parse the stat file
				if(processRecord != null){
					processRecord.clockTick = clockTick;
					process_resources.put(processRecord);
					outputRecordsGenerated++;
				}
			}
		} catch (Exception e) {}
		long dur = System.currentTimeMillis() - st;
		//System.err.println("Generated " + outputRecordsGenerated + " process output records in " + dur + " ms, " + ((double)outputRecordsGenerated / (double)dur));
		return true;
	}
	private boolean generateDifferentialProcessResourceRecords(long previousTime, long now) {
		long st = System.currentTimeMillis();
		int outputRecordsGenerated=0, counterRecordsGenerated=0;
		
		try {
			FilenameFilter fnFilter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					if(name.charAt(0) >= '1' && name.charAt(0) <= '9')
						return true;
					return false;
				}
			};
			File ppFile = new File("/proc");
			File[] pPaths = ppFile.listFiles(fnFilter);
			
			//For each process in /proc
			for (int pn = 0; pn<pPaths.length; pn++){
				//Retrieve the process counters contained in /proc/[pid]/stat
				ProcessResourceRecord processRecord = ProcessResourceRecord.produceRecord(pPaths[pn].toString() + "/stat");
				
				//Move on to the next process if we failed to parse the stat file
				if(processRecord == null) continue;

				//Check whether we saw this process the last time we gathered process_resources metrics
				if(processResourceCounterMap.containsKey(pPaths[pn].getName())){
					//If we've seen this process before, then diff the counters and put the diff record in the output queue
					process_resources.put(ProcessResourceRecord.diff(processResourceCounterMap.get(pPaths[pn].getName()), processRecord, clockTick));
					outputRecordsGenerated++;
				}
				processResourceCounterMap.put(pPaths[pn].getName(), processRecord);
				counterRecordsGenerated++;
			}
			//All counters for running processes should have been updated in the records in processResourceCounterMap
			//Remove any entries in processResourceCounterMap that we did not see this time through (e.g. that don't have timestamp >= time we started gathering this round)
			Iterator<Map.Entry<String, ProcessResourceRecord>>i = processResourceCounterMap.entrySet().iterator();
			while(i.hasNext()) {
				Map.Entry<String, ProcessResourceRecord> pair = (Map.Entry<String, ProcessResourceRecord>)i.next();
				if(pair.getValue().timestamp < now){
					processResourceCounterMap.remove(pair.getKey());
				}
			}
		} catch (Exception e) {}
		long dur = System.currentTimeMillis() - st;
		System.err.println("Generated " + outputRecordsGenerated + " process output records and " + counterRecordsGenerated + " counter records" + " in " + dur + " ms, " + ((double)outputRecordsGenerated / (double)dur));
		return true;
	}
	private boolean initialize_network_interfaces(int periodicity){
		networkInterfaceCounterMap = new ConcurrentHashMap<String, NetworkInterfaceRecord>(64);
		network_interfaces = new SubscriptionRecordQueue("network.interfaces", 600);
		network_interfaces.registerProducer("/proc");
		GatherMetricEvent event = new GatherMetricEvent(0l, 0l, "network.interfaces", periodicity);
		metricSchedule.add(event);
		nameToRecordQueueMap.put("network.interfaces",  network_interfaces);
		return true;
	}
	private boolean generateNetworkInterfaceRecords(long previousTime, long now) {
		try {
			for (NetworkInterface i : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if(i.getName().equals("lo")){
					continue;
				}
				NetworkInterfaceRecord newrecord = NetworkInterfaceRecord.produceRecord(i.getName());
				network_interfaces.put(newrecord);
			}
		} catch (Exception e) {
			System.err.println("Failed to list network interfaces");
			e.printStackTrace();
			return false;
		}
		return true;
	}
	private boolean generateDifferentialNetworkInterfaceRecords(long previousTime, long now) {
		try {
			for (NetworkInterface i : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if(i.getName().equals("lo")){
					continue;
				}
				NetworkInterfaceRecord newrecord = new NetworkInterfaceRecord();
				newrecord.timestamp = now;
				newrecord.name = i.getName();
				//System.err.println("Got NIC name: " + newrecord.name);
				newrecord.duplex = readStringFromFile("/sys/class/net/" + newrecord.name + "/duplex");
				if(newrecord.duplex.equals("full")){
					newrecord.fullDuplex = true;
				} else {
					newrecord.fullDuplex = false;
				}
				newrecord.carrier = readIntFromFile("/sys/class/net/" + newrecord.name + "/carrier");
				newrecord.speed = readIntFromFile("/sys/class/net/" + newrecord.name + "/speed");
				newrecord.tx_queue_len = readIntFromFile("/sys/class/net/" + newrecord.name + "/tx_queue_len");
				newrecord.collisions = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/collisions");
				newrecord.rx_bytes = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/rx_bytes");
				newrecord.rx_dropped = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/rx_dropped");
				newrecord.rx_errors = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/rx_errors");
				newrecord.rx_packets = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/rx_packets");
				newrecord.tx_bytes = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/tx_bytes");
				newrecord.tx_dropped = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/tx_dropped");
				newrecord.tx_errors = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/tx_errors");
				newrecord.tx_packets = readBigIntegerFromFile("/sys/class/net/" + newrecord.name + "/statistics/tx_packets");
				
				if(networkInterfaceCounterMap.containsKey(newrecord.name)){
					//Use the old record as the diff record so we can avoid needlessly creating a new DiskstatRecord
					NetworkInterfaceRecord oldrecord = networkInterfaceCounterMap.get(newrecord.name);
					oldrecord.durationms = now - oldrecord.timestamp;
					oldrecord.carrier = newrecord.carrier;
					oldrecord.speed = newrecord.speed;
					oldrecord.tx_queue_len = newrecord.tx_queue_len;
					oldrecord.collisions = newrecord.collisions.subtract(oldrecord.collisions);
					oldrecord.rx_bytes = newrecord.rx_bytes.subtract(oldrecord.rx_bytes);
					oldrecord.rx_dropped = newrecord.rx_dropped.subtract(oldrecord.rx_dropped);
					oldrecord.rx_errors = newrecord.rx_errors.subtract(oldrecord.rx_errors);
					oldrecord.rx_packets = newrecord.rx_packets.subtract(oldrecord.rx_packets);
					oldrecord.tx_bytes = newrecord.tx_bytes.subtract(oldrecord.tx_bytes);
					oldrecord.tx_dropped = newrecord.tx_dropped.subtract(oldrecord.tx_dropped);
					oldrecord.tx_errors = newrecord.tx_errors.subtract(oldrecord.tx_errors);
					oldrecord.tx_packets = newrecord.tx_packets.subtract(oldrecord.tx_packets);
					oldrecord.timestamp = now;
					network_interfaces.put(oldrecord);
					//System.err.println("Generated network_interfaces: " + oldrecord.toString());
				}
				networkInterfaceCounterMap.put(newrecord.name, newrecord);
			}
			//Remove any entries from networkIntefaceCounterMap for NICs that we didn't see this time through
			//NICs can fail/disappear so we should cleanup internal data when that happens so we don't end up with bad stats.
			Iterator<Map.Entry<String, NetworkInterfaceRecord>>i = networkInterfaceCounterMap.entrySet().iterator();
			while(i.hasNext()) {
				Map.Entry<String, NetworkInterfaceRecord> pair = (Map.Entry<String, NetworkInterfaceRecord>)i.next();
				if(pair.getValue().timestamp != now){
					System.err.println("A NIC disappeared from /sys/class/net/: " + pair.getKey());
					networkInterfaceCounterMap.remove(pair.getKey());
				}
			}
		} catch (Exception e) {
			System.err.println("Failed to list network interfaces");
			e.printStackTrace();
			return false;
		}
		return true;
	}
	private boolean initialize_memory_system(int periodicity){
		systemMemoryCounters = null;
		memory_system = new SubscriptionRecordQueue("memory.system", 300);
		memory_system.registerProducer("/proc");
		GatherMetricEvent event = new GatherMetricEvent(0l, 0l, "memory.system", periodicity);
		metricSchedule.add(event);
		nameToRecordQueueMap.put("memory.system",  memory_system);
		return true;
	}	
	private boolean generateSystemMemoryRecord(long previousTime, long now) {
		SystemMemoryRecord newrecord = SystemMemoryRecord.produceRecord(proc_meminfo, proc_vmstat);
		if(newrecord != null){
			memory_system.put(newrecord);
			return true;
		}
		return false;
	}
	private boolean generateDifferentialSystemMemoryRecord(long previousTime, long now) {
		String[] parts;
		String line;
		SystemMemoryRecord newrecord = new SystemMemoryRecord();
		newrecord.timestamp = now;
		try{
			proc_meminfo.seek(0l);
			while ( (line = proc_meminfo.readLine()) != null) {
				parts = line.trim().split("\\s+");
				BigInteger val = null;
				if(parts.length >= 2){
					val = new BigInteger(parts[1]);
					if(parts.length == 3){
						if(	parts[2].equals("kB") || 
							parts[2].equals("k") || 
							parts[2].equals("K") || 
							parts[2].equals("KB") ) {
							val = val.multiply(new BigInteger("1024"));
						} else if( 	parts[2].equals("mB") ||
									parts[2].equals("MB") ||
									parts[2].equals("m") ||
									parts[2].equals("M") ) {
							val = val.multiply(new BigInteger("1048576"));
						} else {
							System.err.println("Unknown unit type in /proc/meminfo " + parts[2]);
							return false;
						}
					} else if (parts.length > 3) {
						System.err.println("Expecting lines with 2 or 3 fields in /proc/meminfo but found " + parts.length);
						return false;
					}
					newrecord.setValueByName(parts[0].split(":")[0], val);
				} else {
					System.err.println("Expecting lines with 2 or 3 fields in /proc/meminfo but found " + parts.length);
					return false;
				}
			}
			proc_vmstat.seek(0l);
			while ( (line = proc_vmstat.readLine()) != null) {
				parts = line.trim().split("\\s+");
				BigInteger val = null;
				if(parts.length == 2){
					val = new BigInteger(parts[1]);
					newrecord.setValueByName(parts[0], val);
				} else {
					System.err.println("Expecting lines with 2 fields in /proc/vmstat but found " + parts.length);
					return false;
				}
			}
			
			if(systemMemoryCounters != null){
				SystemMemoryRecord diff = SystemMemoryRecord.diff(systemMemoryCounters, newrecord);
				if(diff != null){
					memory_system.put(diff);
				} else {
					System.err.println("Failed to diff new and old memory_system counters");
					return false;
				}
			}
			systemMemoryCounters = newrecord;
		} catch (Exception e) {
			System.err.println("Caught an exception while processing /proc/meminfo");
			e.printStackTrace();
		}
		return true;
	}
	private boolean initialize_disk(int periodicity){
		systemDiskCounterMap = new ConcurrentHashMap<String, DiskstatRecord>(512);
		disk_system = new SubscriptionRecordQueue("disk.system", 4096);
		disk_system.registerProducer("/proc");
		GatherMetricEvent event = new GatherMetricEvent(0l, 0l, "disk.system", periodicity);
		metricSchedule.add(event);
		nameToRecordQueueMap.put("disk.system", disk_system);
		return true;
	}
	private boolean generateDiskRecord(long previousTime, long now) {
		String[] parts;
		String line;

		try {
			proc_diskstats.seek(0l);
			while ( (line = proc_diskstats.readLine()) != null) {
				DiskstatRecord newrecord = new DiskstatRecord();
				parts = line.trim().split("\\s+");
				if(parts.length < 14){
					System.err.println("Each line in /proc/diskstats is expected to have 14 fields, found " + parts.length);
					return false;
				}
				
				//Check if it's a physical device, we only want stats for physical devices
				String sys_device_path = "/sys/block/" + parts[2].replaceAll("/", "!") + "/device";
				File f = new File(sys_device_path);
				if(f.exists()){	
					newrecord.major_number = Integer.parseInt(parts[0]);
					newrecord.minor_mumber = Integer.parseInt(parts[1]);
					newrecord.device_name = parts[2];
					newrecord.reads_completed_successfully = new BigInteger(parts[3]);
					newrecord.reads_merged = new BigInteger(parts[4]);
					newrecord.sectors_read = new BigInteger(parts[5]);
					newrecord.time_spent_reading = new BigInteger(parts[6]);
					newrecord.writes_completed = new BigInteger(parts[7]);
					newrecord.writes_merged = new BigInteger(parts[8]);
					newrecord.sectors_written = new BigInteger(parts[9]);
					newrecord.time_spent_writing = new BigInteger(parts[10]);
					newrecord.IOs_currently_in_progress = new BigInteger(parts[11]);
					newrecord.time_spent_doing_IOs = new BigInteger(parts[12]);
					newrecord.weighted_time_spent_doing_IOs = new BigInteger(parts[13]);
					newrecord.timestamp = now;
					disk_system.put(newrecord);
				} 
			}
		} catch (Exception e) {
			System.err.println("Failed reading disksstats");
			e.printStackTrace();
			return false;
		}
		return true;
	}
	private boolean generateDifferentialDiskRecord(long previousTime, long now) {
		String[] parts;
		String line;

		try {
			proc_diskstats.seek(0l);
			while ( (line = proc_diskstats.readLine()) != null) {
				DiskstatRecord newrecord = new DiskstatRecord();
				parts = line.trim().split("\\s+");
				if(parts.length < 14){
					System.err.println("Each line in /proc/diskstats is expected to have 14 fields, found " + parts.length);
					return false;
				}
				
				//Check if it's a physical device, we only want stats for physical devices
				String sys_device_path = "/sys/block/" + parts[2].replaceAll("/", "!") + "/device";
				File f = new File(sys_device_path);
				if(f.exists()){	
					newrecord.major_number = Integer.parseInt(parts[0]);
					newrecord.minor_mumber = Integer.parseInt(parts[1]);
					newrecord.device_name = parts[2];
					newrecord.reads_completed_successfully = new BigInteger(parts[3]);
					newrecord.reads_merged = new BigInteger(parts[4]);
					newrecord.sectors_read = new BigInteger(parts[5]);
					newrecord.time_spent_reading = new BigInteger(parts[6]);
					newrecord.writes_completed = new BigInteger(parts[7]);
					newrecord.writes_merged = new BigInteger(parts[8]);
					newrecord.sectors_written = new BigInteger(parts[9]);
					newrecord.time_spent_writing = new BigInteger(parts[10]);
					newrecord.IOs_currently_in_progress = new BigInteger(parts[11]);
					newrecord.time_spent_doing_IOs = new BigInteger(parts[12]);
					newrecord.weighted_time_spent_doing_IOs = new BigInteger(parts[13]);
					newrecord.timestamp = now;
					
					if(systemDiskCounterMap.containsKey(newrecord.device_name)){
						//Use the old record as the diff record so we can avoid needlessly creating a new DiskstatRecord
						DiskstatRecord oldrecord = systemDiskCounterMap.get(newrecord.device_name);
						oldrecord.durationms = now - oldrecord.timestamp;
						oldrecord.IOs_currently_in_progress = newrecord.IOs_currently_in_progress;
						oldrecord.previousTimestamp = oldrecord.timestamp;
						oldrecord.reads_completed_successfully = newrecord.reads_completed_successfully.subtract(oldrecord.reads_completed_successfully);
						oldrecord.reads_merged = newrecord.reads_merged.subtract(oldrecord.reads_merged);
						oldrecord.sectors_read = newrecord.sectors_read.subtract(oldrecord.sectors_read);
						oldrecord.sectors_written = newrecord.sectors_written.subtract(oldrecord.sectors_written);
						oldrecord.time_spent_doing_IOs = newrecord.time_spent_doing_IOs.subtract(oldrecord.time_spent_doing_IOs);
						oldrecord.time_spent_reading = newrecord.time_spent_reading.subtract(oldrecord.time_spent_reading);
						oldrecord.time_spent_writing = newrecord.time_spent_writing.subtract(oldrecord.time_spent_writing);
						oldrecord.timestamp = now;
						oldrecord.weighted_time_spent_doing_IOs = newrecord.weighted_time_spent_doing_IOs.subtract(oldrecord.weighted_time_spent_doing_IOs);
						oldrecord.writes_completed = newrecord.writes_completed.subtract(oldrecord.writes_completed);
						oldrecord.writes_merged = newrecord.writes_merged.subtract(oldrecord.writes_merged);
						disk_system.put(oldrecord);
						//System.err.println("Generated DiskstatRecord: " + oldrecord.toString());
					}
					systemDiskCounterMap.put(newrecord.device_name, newrecord);
				} 
			}
			
			//Remove any entries from diskCounterMap for disks that we didn't see this time through
			//Disks can fail/disappear so we should cleanup internal data when that happens so we don't end up with bad stats.
			Iterator<Map.Entry<String, DiskstatRecord>>i = systemDiskCounterMap.entrySet().iterator();
			while(i.hasNext()) {
				Map.Entry<String, DiskstatRecord> pair = (Map.Entry<String, DiskstatRecord>)i.next();
				if(pair.getValue().timestamp != now){
					System.err.println("A disk disappeared from /proc/diskstats: " + pair.getValue());
					systemDiskCounterMap.remove(pair.getKey());
				}
			}
		} catch (Exception e) {
			System.err.println("Failed reading disksstats");
			e.printStackTrace();
			return false;
		}
		return true;
	}
	private boolean initialize_cpu_system(int periodicity){
		systemCpuCounters = null;
		cpu_system = new SubscriptionRecordQueue("cpu.system", 300);
		cpu_system.registerProducer("/proc");
		GatherMetricEvent event = new GatherMetricEvent(0l, 0l, "cpu.system", periodicity);
		metricSchedule.add(event);
		nameToRecordQueueMap.put("cpu.system", cpu_system);
		return true;
	}
	private boolean generateSystemCpuRecord(long previousTime, long now) {
		SystemCpuRecord newrecord = SystemCpuRecord.produceRecord(proc_stat);
		if(newrecord != null){
			cpu_system.put(newrecord);
			return true;
		}
		return false;
	}

	private boolean initialize_tcp_connection_stats(int periodicity){
		tcp_connection_stats = new SubscriptionRecordQueue("tcp.connection.stats", 65536);
		tcp_connection_stats.registerProducer("/proc");
		GatherMetricEvent event = new GatherMetricEvent(0l, 0l, "tcp.connection.stats", periodicity);
		metricSchedule.add(event);
		nameToRecordQueueMap.put("tcp.connection.stats", tcp_connection_stats);
		return true;
	}
	private boolean generateTcpConnectionStatRecords(long previousTime, long now) {
		return TcpConnectionStatRecord.produceRecords(proc_net_tcp, tcp_connection_stats);

	}
	private void setClockTick(){
		//CLK_TCK is needed to calculate CPU usage of threads/processes based on utime and stime fields which are measured in this unit
		ProcessBuilder processBuilder = new ProcessBuilder(new String[]{"getconf", "CLK_TCK"});
		Process process = null;
		int ret = -1;
		try{
			process = processBuilder.start();
			ret = process.waitFor();
		} catch (Exception e) {
			System.err.println("Failed to wait for getconf to complete");
			e.printStackTrace();
			System.exit(1);
		}
		if(ret == 0){
			InputStream stdout = process.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
			try {
				String line = br.readLine();
				if (line != null){
					clockTick = Integer.parseInt(line);
					if(clockTick < 1){
						System.err.println("Failed to retrieve sysconfig(\"CLK_TCK\")");
						System.exit(1);
					}
					clockTickms = 1000d / (double)clockTick;
				}
			} catch (Exception e) {
				System.err.println("Failed to read getconf output");
				e.printStackTrace();
			}
			System.err.println("Retrieved CLK_TLK=" + clockTick);
		} else {
			System.err.println("Failed to run \"getconf CLK_TCK\"");
			System.exit(1);
		}
	}
}
