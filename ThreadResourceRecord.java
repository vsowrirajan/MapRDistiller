import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;


public class ThreadResourceRecord extends Record {
	String comm = null;
	char state;
	int pid, ppid, clockTick=0;
	long starttime;
	Double cpuUtilPct = -1d;
	BigInteger delayacct_blkio_ticks = null;
	BigInteger guest_time = null;
	BigInteger majflt = null;
	BigInteger minflt = null;
	BigInteger stime = null;
	BigInteger utime = null;

	ThreadResourceRecord(){
		super();
		this.clockTick=0;
	}
	ThreadResourceRecord(int clockTick){
		super();
		this.clockTick = clockTick;
	}
	ThreadResourceRecord(ThreadResourceRecord pr){
		super(pr);
		this.comm = pr.comm;
		this.state = pr.state;
		this.pid = pr.pid;
		this.ppid = pr.ppid;
		this.minflt = pr.minflt;
		this.majflt = pr.majflt;
		this.utime = pr.utime;
		this.stime = pr.stime;
		this.starttime = pr.starttime;
		this.delayacct_blkio_ticks = pr.delayacct_blkio_ticks;
		this.guest_time = pr.guest_time;
		this.clockTick = pr.clockTick;
	}
	public String toString(){
		return super.toString() + " thread.resources: " + pid + " " + comm + " " + state + " " + ppid;
	}
	public static ThreadResourceRecord produceRecord(String path, int ppid){
		ThreadResourceRecord rec = new ThreadResourceRecord();
		try {
			StringLong sl = readLineFromStatFile(path);
			if(sl != null){
				rec = parseLineFromThreadStatFile(sl, rec);
				if(rec != null){
					rec.ppid = ppid;
					return rec;
				}
			}
		} catch (Exception e) {
			//System.err.println("exception");
			//e.printStackTrace();
			//This is likely because the /proc/[pid]/task/[tid] dir no longer exists (because the thread or process stopped running)	
		}
		return null;
	}
	private static StringLong readLineFromStatFile(String path){
		int bs = 600; //600 bytes should be enough to hold contents of /proc/[pid]/stat or /proc/[pid]/task/[tid]/stat 
		
		StringLong retVal = new StringLong();
		FileChannel fc = null;
		try {
			fc = FileChannel.open(Paths.get(path));
			ByteBuffer b = ByteBuffer.allocate(bs);
			int br = fc.read(b);
			retVal.longVal = System.currentTimeMillis();
			fc.close();
			if(br > 0 && br < bs){
				retVal.stringVal = new String(b.array());
				return retVal;
			} else {
				return null;
			}
		} catch (Exception e){}
		if(fc != null){
			try{
				fc.close();
			} catch (Exception e) {}
		}
		return null;
		
		/**
		FileInputStream s = null;
		try{
			s = new FileInputStream(new File(path));
			byte[] b = new byte[bs];
			int br = s.read(b,0,bs);
			if(!(br==0) && !(br==-1) || !(br==bs)){
				String line = new String(b);
				s.close();
				return line;
			}
		} catch (Exception e){ 
			//System.err.println("e3");
			//e.printStackTrace();
			//We should only be here when process/thread stops running
		}
		if(s != null){
			try {
				s.close();
			} catch (Exception e) {}
		}
		return null;
		**/
	}
	public static ThreadResourceRecord parseLineFromThreadStatFile(StringLong sl, ThreadResourceRecord rec){
		String[] parts = sl.stringVal.split("\\)", 2)[1].trim().split("\\s+");
		if(parts.length<42){ //Expect 44 values in /proc/[pid]/task/[tid]/stat based on Linux kernel version used for this dev.
			return null;
		}
		rec.pid = Integer.parseInt(sl.stringVal.split("\\s+", 2)[0]);
		rec.comm = "(" + sl.stringVal.split("\\(", 2)[1].split("\\)", 2)[0] + ")";
		rec.state = parts[0].charAt(0);
		rec.minflt = new BigInteger(parts[7]);
		rec.majflt = new BigInteger(parts[9]);
		rec.utime = new BigInteger(parts[11]);
		rec.stime = new BigInteger(parts[12]);
		rec.starttime = Integer.parseInt(parts[19]);
		rec.delayacct_blkio_ticks = new BigInteger(parts[39]);
		rec.guest_time = new BigInteger(parts[40]);
		rec.timestamp = sl.longVal;
		return rec;
	}
	public static ThreadResourceRecord diff(ThreadResourceRecord oldRecord, ThreadResourceRecord newRecord){
		//This function should be called to diff ThreadResourceRecords when the clockTick field of those records is populated.
		//If this is called against records without clockTick populated then no cpu utilization will be calculated (because we can't).
		
		//Only diff the records if they are from the same thread, as identified by the TID and starttime.
		//This works under the assumption that the system is not able to cycle through all process IDs and reuse them within a single
		//tick of the starttime clock
		if(	oldRecord.pid != newRecord.pid 				||
			oldRecord.starttime != newRecord.starttime 	)
			return null;
			
		ThreadResourceRecord diffRecord = new ThreadResourceRecord();
		diffRecord.previousTimestamp = oldRecord.timestamp;
		diffRecord.timestamp = newRecord.timestamp;
		diffRecord.durationms = newRecord.timestamp - oldRecord.previousTimestamp;
		diffRecord.comm = newRecord.comm;
		diffRecord.state = newRecord.state;
		diffRecord.pid = newRecord.pid;
		diffRecord.ppid = newRecord.ppid;
		diffRecord.starttime = newRecord.starttime;
		diffRecord.minflt = newRecord.minflt.subtract(oldRecord.minflt);
		diffRecord.majflt = newRecord.majflt.subtract(oldRecord.majflt);
		diffRecord.utime = newRecord.utime.subtract(oldRecord.utime);
		diffRecord.stime = newRecord.stime.subtract(oldRecord.stime);
		diffRecord.delayacct_blkio_ticks = newRecord.delayacct_blkio_ticks.subtract(oldRecord.delayacct_blkio_ticks);
		diffRecord.guest_time = newRecord.guest_time.subtract(oldRecord.guest_time);
		diffRecord.clockTick = newRecord.clockTick;
		
		//Derived values:
		if(diffRecord.clockTick > 0){
			diffRecord.cpuUtilPct = diffRecord.utime.add(diffRecord.stime).doubleValue() / 					//The amount of jiffies used by the process over the duration
									(((double)(diffRecord.clockTick * diffRecord.durationms)) / 1000d);	//Divided by the amonut of jiffies that elapsed during the duration
		} else {
			diffRecord.cpuUtilPct = -1d;
		}
		return diffRecord;
	}
	public static ThreadResourceRecord diff(ThreadResourceRecord oldRecord, ThreadResourceRecord newRecord, int clockTick){
		//This function should be called to diff ThreadResourceRecords when the clockTick field of those records is populated.
		//If this is called against records without clockTick populated then no cpu utilization will be calculated (because we can't).
		
		//Only diff the records if they are from the same thread, as identified by the TID and starttime.
		//This works under the assumption that the system is not able to cycle through all process IDs and reuse them within a single
		//tick of the starttime clock
		if(	oldRecord.pid != newRecord.pid 				||
			oldRecord.starttime != newRecord.starttime 	)
			return null;
			
		ThreadResourceRecord diffRecord = new ThreadResourceRecord();
		diffRecord.previousTimestamp = oldRecord.timestamp;
		diffRecord.timestamp = newRecord.timestamp;
		diffRecord.durationms = newRecord.timestamp - oldRecord.previousTimestamp;
		diffRecord.comm = newRecord.comm;
		diffRecord.state = newRecord.state;
		diffRecord.pid = newRecord.pid;
		diffRecord.ppid = newRecord.ppid;
		diffRecord.starttime = newRecord.starttime;
		diffRecord.minflt = newRecord.minflt.subtract(oldRecord.minflt);
		diffRecord.majflt = newRecord.majflt.subtract(oldRecord.majflt);
		diffRecord.utime = newRecord.utime.subtract(oldRecord.utime);
		diffRecord.stime = newRecord.stime.subtract(oldRecord.stime);
		diffRecord.delayacct_blkio_ticks = newRecord.delayacct_blkio_ticks.subtract(oldRecord.delayacct_blkio_ticks);
		diffRecord.guest_time = newRecord.guest_time.subtract(oldRecord.guest_time);
		diffRecord.clockTick = newRecord.clockTick;
		
		//Derived values:
		if(clockTick > 0){
			diffRecord.cpuUtilPct = diffRecord.utime.add(diffRecord.stime).doubleValue() / 					//The amount of jiffies used by the process over the duration
									(((double)(clockTick * diffRecord.durationms)) / 1000d);	//Divided by the amonut of jiffies that elapsed during the duration
			diffRecord.clockTick = clockTick;
		} else {
			diffRecord.cpuUtilPct = -1d;
		}
		return diffRecord;
	}
}
