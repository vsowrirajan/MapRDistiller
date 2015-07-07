import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;

public class ProcessResourceRecord extends Record {
	String comm = null;
	char state;
	
	// Variables from /proc/[pid]/stat that we actually parse/use
	int pid, ppid, pgrp, num_threads, clockTick=0;
	long starttime;
	double cpuUtilPct = -1d;
	BigInteger cguest_time = null;
	BigInteger cmajflt = null;
	BigInteger cminflt = null;
	BigInteger cstime = null;
	BigInteger cutime = null;
	BigInteger delayacct_blkio_ticks = null;
	BigInteger guest_time = null;
	BigInteger majflt = null;
	BigInteger minflt = null;
	BigInteger rss = null;
	BigInteger rsslim = null;
	BigInteger stime = null;
	BigInteger utime = null;
	BigInteger vsize = null;

	//Variables from /proc/[pid]/stat that we do NOT use/parse
	/**
	BigInteger blocked = null;
	BigInteger cnswap = null;
	BigInteger endcode = null;
	BigInteger flags = null;
	BigInteger itrealvalue = null;
	BigInteger kstkeip = null;
	BigInteger kstkesp = null;
	BigInteger nice = null;
	BigInteger nswap = null;
	BigInteger policy = null;
	BigInteger priority = null;
	BigInteger processor = null;
	BigInteger rsslim = null;
	BigInteger rt_priority = null;
	BigInteger session = null;
	BigInteger sigcatch = null;
	BigInteger sigignore = null;
	BigInteger signal = null;
	BigInteger startcode = null;
	BigInteger startstack = null;
	BigInteger tpgid = null;
	BigInteger tty_nr = null;
	BigInteger wchan = null;
	**/

	ProcessResourceRecord(){
		super();
		this.clockTick=0;
	}
	ProcessResourceRecord(int clockTick){
		super();
		this.clockTick = clockTick;
	}
	ProcessResourceRecord(ProcessResourceRecord pr){
		super(pr);
		this.comm = pr.comm;
		this.state = pr.state;
		this.pid = pr.pid;
		this.ppid = pr.ppid;
		this.pgrp = pr.pgrp;
		this.minflt = pr.minflt;
		this.cminflt = pr.cminflt;
		this.majflt = pr.majflt;
		this.cmajflt = pr.cmajflt;
		this.utime = pr.utime;
		this.stime = pr.stime;
		this.cutime = pr.cutime;
		this.cstime = pr.cstime;
		this.num_threads = pr.num_threads;
		this.starttime = pr.starttime;
		this.vsize = pr.vsize;
		this.rss = pr.rss;
		this.rsslim = pr.rsslim;
		this.delayacct_blkio_ticks = pr.delayacct_blkio_ticks;
		this.guest_time = pr.guest_time;
		this.cguest_time = pr.cguest_time;
		this.cpuUtilPct = pr.cpuUtilPct;
		this.clockTick = pr.clockTick;
	}
	public String toString(){
		return super.toString() + " process.resources: " + pid + " " + comm + " " + state + " " + ppid + " " + num_threads;
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
			byte[] b = new byte[1024];
			int br = s.read(b,0,1024);
			if(!(br==0) && !(br==-1) && !(br==1024)){
				String line = new String(b);
				s.close();
				return line;
			} else {
				System.err.println("Read for " + path + " return " + br );
			}
		} catch (Exception e){ 
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
	public static ProcessResourceRecord parseLineFromProcessStatFile(StringLong sl, ProcessResourceRecord rec){
		String[] parts = sl.stringVal.split("\\)", 2)[1].trim().split("\\s+");
		if(parts.length<42){ //Expect 44 values in /proc/[pid]/stat based on Linux kernel version used for this dev.
			return null;
		}
		//Newer Linux kernels may have more fields in stat file... this parser will need to be updated for newer kernels once we support them
		//Parsing/saving of some fields is commented out because... well... I don't care about thsoe stats and probably no one else does in 99.99% of cases
		rec.pid = Integer.parseInt(sl.stringVal.split("\\s+", 2)[0]);
		rec.comm = "(" + sl.stringVal.split("\\(", 2)[1].split("\\)", 2)[0] + ")";
		rec.state = parts[0].charAt(0);
		rec.ppid = Integer.parseInt(parts[1]);
		rec.pgrp = Integer.parseInt(parts[2]);
		rec.minflt = new BigInteger(parts[7]);
		rec.cminflt = new BigInteger(parts[8]);
		rec.majflt = new BigInteger(parts[9]);
		rec.cmajflt = new BigInteger(parts[10]);
		rec.utime = new BigInteger(parts[11]);
		rec.stime = new BigInteger(parts[12]);
		rec.cutime = new BigInteger(parts[13]);
		rec.cstime = new BigInteger(parts[14]);
		rec.num_threads = Integer.parseInt(parts[17]);
		rec.starttime = Integer.parseInt(parts[19]);
		rec.vsize = new BigInteger(parts[20]);
		rec.rss = new BigInteger(parts[21]);
		rec.rsslim = new BigInteger(parts[22]);
		rec.delayacct_blkio_ticks = new BigInteger(parts[39]);
		rec.guest_time = new BigInteger(parts[40]);
		rec.cguest_time = new BigInteger(parts[41]);
		rec.timestamp = sl.longVal;
		return rec;
	}
	public static ProcessResourceRecord produceRecord(String path){
		ProcessResourceRecord rec = new ProcessResourceRecord();
		try {
			StringLong sl = readLineFromStatFile(path);
			if(sl != null){
				return parseLineFromProcessStatFile(sl, rec);
			}
		} catch (Exception e) {
			//This is likely because the /proc/[pid] dir no longer exists (because the process stopped running)	
		}
		return null;
	}
	public static ProcessResourceRecord diff(ProcessResourceRecord oldRecord, ProcessResourceRecord newRecord){
		//This function should be called to diff ProcessResourceRecords when the clockTick field of those records is populated.
		//If this is called against records without clockTick populated then no cpu utilization will be calculated (because we can't).
		
		//Only diff the records if they are from the same process, as identified by the PID and starttime, and if the oldRecord is
		//indeed older than the newRecord by timestamp.
		//This works under the assumption that the system is not able to cycle through all process IDs and reuse them within a single
		//tick of the starttime clock
		if(	oldRecord.pid != newRecord.pid 				||
			oldRecord.starttime != newRecord.starttime 	||
			oldRecord.timestamp >= newRecord.timestamp	)
			return null;
			
		ProcessResourceRecord diffRecord = new ProcessResourceRecord();
		diffRecord.previousTimestamp = oldRecord.timestamp;
		diffRecord.timestamp = newRecord.timestamp;
		diffRecord.durationms = newRecord.timestamp - oldRecord.previousTimestamp;
		diffRecord.comm = newRecord.comm;
		diffRecord.state = newRecord.state;
		diffRecord.pid = newRecord.pid;
		diffRecord.ppid = newRecord.ppid;
		diffRecord.pgrp = newRecord.pgrp;
		diffRecord.num_threads = newRecord.num_threads;
		diffRecord.starttime = newRecord.starttime;
		diffRecord.vsize = newRecord.vsize;
		diffRecord.rss = newRecord.rss;
		diffRecord.rsslim = newRecord.rsslim;
		diffRecord.minflt = newRecord.minflt.subtract(oldRecord.minflt);
		diffRecord.cminflt = newRecord.cminflt.subtract(oldRecord.cminflt);
		diffRecord.cmajflt = newRecord.cmajflt.subtract(oldRecord.cmajflt);
		diffRecord.cutime = newRecord.cutime.subtract(oldRecord.cutime);
		diffRecord.cstime = newRecord.cstime.subtract(oldRecord.cstime);
		diffRecord.cguest_time = newRecord.cguest_time.subtract(oldRecord.cguest_time);
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
	public static ProcessResourceRecord diff(ProcessResourceRecord oldRecord, ProcessResourceRecord newRecord, int clockTick){
		//This function should be called to diff ProcessResourceRecords when the clockTick field of those records is not populated,
		//or when it is desirable to override clockTick saved in the records (not sure when that would ever be)
		
		//Only diff the records if they are from the same process, as identified by the PID and starttime.
		//This works under the assumption that the system is not able to cycle through all process IDs and reuse them within a single
		//tick of the starttime clock
		if(	oldRecord.pid != newRecord.pid 				||
			oldRecord.starttime != newRecord.starttime 	)
			return null;

			
		ProcessResourceRecord diffRecord = new ProcessResourceRecord();
		diffRecord.previousTimestamp = oldRecord.timestamp;
		diffRecord.timestamp = newRecord.timestamp;
		diffRecord.durationms = newRecord.timestamp - oldRecord.previousTimestamp;
		diffRecord.comm = newRecord.comm;
		diffRecord.state = newRecord.state;
		diffRecord.pid = newRecord.pid;
		diffRecord.ppid = newRecord.ppid;
		diffRecord.pgrp = newRecord.pgrp;
		diffRecord.num_threads = newRecord.num_threads;
		diffRecord.starttime = newRecord.starttime;
		diffRecord.vsize = newRecord.vsize;
		diffRecord.rss = newRecord.rss;
		diffRecord.rsslim = newRecord.rsslim;
		diffRecord.minflt = newRecord.minflt.subtract(oldRecord.minflt);
		diffRecord.cminflt = newRecord.cminflt.subtract(oldRecord.cminflt);
		diffRecord.cmajflt = newRecord.cmajflt.subtract(oldRecord.cmajflt);
		diffRecord.cutime = newRecord.cutime.subtract(oldRecord.cutime);
		diffRecord.cstime = newRecord.cstime.subtract(oldRecord.cstime);
		diffRecord.cguest_time = newRecord.cguest_time.subtract(oldRecord.cguest_time);
		diffRecord.majflt = newRecord.majflt.subtract(oldRecord.majflt);
		diffRecord.utime = newRecord.utime.subtract(oldRecord.utime);
		diffRecord.stime = newRecord.stime.subtract(oldRecord.stime);
		diffRecord.delayacct_blkio_ticks = newRecord.delayacct_blkio_ticks.subtract(oldRecord.delayacct_blkio_ticks);
		diffRecord.guest_time = newRecord.guest_time.subtract(oldRecord.guest_time);
		
		//Derived values:
		if(clockTick > 0){
			diffRecord.cpuUtilPct = diffRecord.utime.add(diffRecord.stime).doubleValue() / 				//The amount of jiffies used by the process over the duration
									(((double)(clockTick * diffRecord.durationms)) / 1000d);	//Divided by the amonut of jiffies that elapsed during the duration
			diffRecord.clockTick = clockTick;
		} else {
			diffRecord.cpuUtilPct = -1d;
		}

		return diffRecord;
	}

}
