import java.math.BigInteger;
import java.io.RandomAccessFile;

public class SystemCpuRecord extends Record {
	/**
	 * DERIVED VALUES
	 * These are variables that are not sourced directly from /proc
	 * These values are computed only for records returned by calls to a diff function of this class
	 */
	double idleCpuUtilPct, iowaitCpuUtilPct;

	/**
	 * RAW VALUES
	 * These are variables whose values are sourced directly from /proc when produceRecord is called
	 */
	BigInteger cpu_user;
	BigInteger cpu_nice;
	BigInteger cpu_sys;
	BigInteger cpu_idle;
	BigInteger cpu_iowait;
	BigInteger cpu_hardirq;
	BigInteger cpu_softirq;
	BigInteger cpu_steal;
	BigInteger cpu_other;
	BigInteger total_jiffies;
	
	SystemCpuRecord(){}
	SystemCpuRecord(SystemCpuRecord r){
		super(r);
		idleCpuUtilPct = r.idleCpuUtilPct;
		iowaitCpuUtilPct = r.iowaitCpuUtilPct;
		cpu_user = r.cpu_user; 
		cpu_sys = r.cpu_sys;
		cpu_idle = r.cpu_idle;
		cpu_iowait = r.cpu_iowait;
		cpu_hardirq = r.cpu_hardirq;
		cpu_softirq = r.cpu_softirq;
		cpu_steal = r.cpu_steal;
		cpu_other = r.cpu_other;
		total_jiffies = r.total_jiffies;
	}
	public String toString(){
		return super.toString() + " SystemCPU user:" + cpu_user + 
				" nice:" + cpu_nice +
				" sys:" + cpu_sys + 
				" idle:" + cpu_idle + 
				" iowait:" + cpu_iowait + 
				" hard:" + cpu_hardirq + 
				" soft:" + cpu_softirq + 
				" steal:" + cpu_steal + 
				" other:" + cpu_other +
				" jiffies:" + total_jiffies + 
				" idleCpuUtilPct:" + idleCpuUtilPct + 
				" iowaitCpuUtilPct:" + iowaitCpuUtilPct;
	}
	
	public static SystemCpuRecord produceRecord(){
		RandomAccessFile proc_stat = null;
		SystemCpuRecord record = null;
		try {
			proc_stat = new RandomAccessFile("/proc/stat", "r");
		} catch (Exception e) {
			System.err.println("Failed to parse /proc/stat");
			e.printStackTrace();
			return null;
		}
		record = produceRecord(proc_stat);
		try {
			proc_stat.close();
		} catch (Exception e) {}
		return record;
	}
	public static SystemCpuRecord produceRecord(RandomAccessFile proc_stat){
		SystemCpuRecord newrecord = new SystemCpuRecord();
		try {
			proc_stat.seek(0l);
			String[] parts = proc_stat.readLine().split("\\s+");
			newrecord.timestamp = System.currentTimeMillis();
			if (parts.length < 9) {
				System.err.println("First line of /proc/stat expected to have 9 or more fields, found " + parts.length);
				return null;
			}
			if (!parts[0].equals("cpu")) {
				System.err.println("First line of /proc/stat expected to start with \"cpu\"");
				return null;
			}
			
			newrecord.cpu_user = new BigInteger(parts[1]);
			newrecord.cpu_nice = new BigInteger(parts[2]);
			newrecord.cpu_sys = new BigInteger(parts[3]);
			newrecord.cpu_idle = new BigInteger(parts[4]);
			newrecord.cpu_iowait = new BigInteger(parts[5]);
			newrecord.cpu_hardirq = new BigInteger(parts[6]);
			newrecord.cpu_softirq = new BigInteger(parts[7]);
			newrecord.cpu_steal = new BigInteger(parts[8]);
			newrecord.cpu_other = new BigInteger("0");
			for(int x=9; x<parts.length; x++){
				newrecord.cpu_other = newrecord.cpu_other.add(new BigInteger(parts[x]));
			}
			newrecord.total_jiffies = 	
				newrecord.cpu_user.add(
				newrecord.cpu_nice.add(
				newrecord.cpu_sys.add(
				newrecord.cpu_idle.add(
				newrecord.cpu_iowait.add(
				newrecord.cpu_hardirq.add(
				newrecord.cpu_softirq.add(
				newrecord.cpu_steal.add(
				newrecord.cpu_other))))))));
			
			return newrecord;
		} catch (Exception e) {
			System.err.println("Failed to generate SystemCpuRecord");
			e.printStackTrace();
		}
		return null;
	}
	public static String[] type1DiffSupport(){
		return new String[] {	"SystemCpuRecord.idleCpuUtilPct",
								"SystemCpuRecord.iowaitCpuUtilPct",
								"SystemCpuRecord.cpu_user",
								"SystemCpuRecord.cpu_nice",
								"SystemCpuRecord.cpu_sys",
								"SystemCpuRecord.cpu_idle",
								"SystemCpuRecord.cpu_iowait",
								"SystemCpuRecord.cpu_hardirq",
								"SystemCpuRecord.cpu_softirq",
								"SystemCpuRecord.cpu_steal",
								"SystemCpuRecord.cpu_other",
								"SystemCpuRecord.total_jiffies"	};
	}
	public static SystemCpuRecord type1Diff(SystemCpuRecord oldRecord, SystemCpuRecord newRecord){
		SystemCpuRecord diffRecord = new SystemCpuRecord();
		return type1Diff(oldRecord, newRecord, diffRecord);
	}
	public static SystemCpuRecord type1Diff(SystemCpuRecord oldRecord, SystemCpuRecord newRecord, SystemCpuRecord diffRecord){
		/**
		 * Not sure if we need this check....
		 */
		//if(oldRecord.timestamp >= newRecord.timestamp){
		//	return null;
		//}
		Record.type1Diff(oldRecord, newRecord, diffRecord);
		diffRecord.cpu_user = newRecord.cpu_user.subtract(oldRecord.cpu_user);
		diffRecord.cpu_nice = newRecord.cpu_nice.subtract(oldRecord.cpu_nice);
		diffRecord.cpu_sys = newRecord.cpu_sys.subtract(oldRecord.cpu_sys);
		diffRecord.cpu_idle = newRecord.cpu_idle.subtract(oldRecord.cpu_idle);
		diffRecord.cpu_iowait = newRecord.cpu_iowait.subtract(oldRecord.cpu_iowait);
		diffRecord.cpu_hardirq = newRecord.cpu_hardirq.subtract(oldRecord.cpu_hardirq);
		diffRecord.cpu_softirq = newRecord.cpu_softirq.subtract(oldRecord.cpu_softirq);
		diffRecord.cpu_steal = newRecord.cpu_steal.subtract(oldRecord.cpu_steal);
		diffRecord.cpu_other = newRecord.cpu_other.subtract(oldRecord.cpu_other);
		diffRecord.total_jiffies = newRecord.total_jiffies.subtract(oldRecord.total_jiffies);

		//Count iowait as idle time since other things could be done during idle time if so needed.
		//We will track iowait % usage separately
		//This var should be used to decide if the system is running low on free CPU capacity
		//Since iowait means there is no useful work to be done by the CPU, we count it idle.
		//That allows this metric to represent true load on the CPUs.
		diffRecord.idleCpuUtilPct = diffRecord.cpu_idle.add(diffRecord.cpu_iowait).doubleValue() / 	//The number of jiffies consumed in idle//iowait
									diffRecord.total_jiffies.doubleValue();							//Divided by the total elapsed jiffies

		//The IO wait time indicates how much running processes are bottlenecking on IO.
		//You can use this in conjunction with the iowaitCpuUtilPct for threads/processes to understand how things are bottlenecking on IO
		diffRecord.iowaitCpuUtilPct = diffRecord.cpu_iowait.doubleValue() / diffRecord.total_jiffies.doubleValue();
		
		return diffRecord;
	}
	public static String[] type2DiffSupport(){
		return null;
	}
	public static SystemCpuRecord type2Diff(SystemCpuRecord[] records){
		return null;
	}
	public static SystemCpuRecord type2Diff(SystemCpuRecord[] records, SystemCpuRecord diffRecord){
		return null;
	}
	public boolean isBelowThreshold(String metricStr, double val){
		if(metricStr.equals("SystemCpuRecord.idleCpuUtilPct"))
			return idleCpuUtilPct < val;
		else if (metricStr.equals("SystemCpuRecord.iowaitCpuUtilPct"))
			return iowaitCpuUtilPct < val;
		return false;
	}

}
