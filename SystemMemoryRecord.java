import java.io.RandomAccessFile;
import java.math.BigInteger;

public class SystemMemoryRecord extends Record {
	/**
	 * DERIVED VALUES
	 * These are variables that are not sourced directly from /proc
	 * These values are computed only for records returned by calls to a diff function of this class
	 */
	double freeMemPct;
	
	/**
	 * RAW VALUES
	 * These are variables whose values are sourced directly from /proc when produceRecord is called
	 */
	//Values from /proc/meminfo
	BigInteger MemTotal = null;
	BigInteger MemFree = null;
	BigInteger Buffers = null;
	BigInteger Cached = null;
	BigInteger SwapCached = null;
	BigInteger Active = null;
	BigInteger Inactive = null;
	BigInteger Active_anon_ = null;
	BigInteger Inactive_anon_ = null;
	BigInteger Active_file_ = null;
	BigInteger Inactive_file_ = null;
	BigInteger Unevictable = null;
	BigInteger Mlocked = null;
	BigInteger SwapTotal = null;
	BigInteger SwapFree = null;
	BigInteger Dirty = null;
	BigInteger Writeback = null;
	BigInteger AnonPages = null;
	BigInteger Mapped = null;
	BigInteger Shmem = null;
	BigInteger Slab = null;
	BigInteger SReclaimable = null;
	BigInteger SUnreclaim = null;
	BigInteger KernelStack = null;
	BigInteger PageTables = null;
	BigInteger NFS_Unstable = null;
	BigInteger Bounce = null;
	BigInteger WritebackTmp = null;
	BigInteger CommitLimit = null;
	BigInteger Committed_AS = null;
	BigInteger VmallocTotal = null;
	BigInteger VmallocUsed = null;
	BigInteger VmallocChunk = null;
	BigInteger HardwareCorrupted = null;
	BigInteger AnonHugePages = null;
	BigInteger HugePages_Total = null;
	BigInteger HugePages_Free = null;
	BigInteger HugePages_Rsvd = null;
	BigInteger HugePages_Surp = null;
	BigInteger Hugepagesize = null;
	BigInteger DirectMap4k = null;
	BigInteger DirectMap2M = null;
	BigInteger DirectMap1G = null;
	
	//Values read from /proc/vmstat
	BigInteger nr_dirty = null;
	BigInteger pswpin = null;
	BigInteger pswpout = null;
	BigInteger pgmajfault = null;
	BigInteger allocstall = null;
	
	SystemMemoryRecord(){}
	SystemMemoryRecord(SystemMemoryRecord r){
		super(r);
		MemTotal = r.MemTotal;
		MemFree = r.MemFree;
		Buffers = r.Buffers;
		Cached = r.Cached;
		SwapCached = r.SwapCached;
		Active = r.Active;
		Inactive = r.Inactive;
		Active_anon_ = r.Active_anon_;
		Inactive_anon_ = r.Inactive_anon_;
		Active_file_ = r.Active_file_;
		Inactive_file_ = r.Inactive_file_;
		Unevictable = r.Unevictable;
		Mlocked = r.Mlocked;
		SwapTotal = r.SwapTotal;
		SwapFree = r.SwapFree;
		Dirty = r.Dirty;
		Writeback = r.Writeback;
		AnonPages = r.AnonPages;
		Mapped = r.Mapped;
		Shmem = r.Shmem;
		Slab = r.Slab;
		SReclaimable = r.SReclaimable;
		SUnreclaim = r.SUnreclaim;
		KernelStack = r.KernelStack;
		PageTables = r.PageTables;
		NFS_Unstable = r.NFS_Unstable;
		Bounce = r.Bounce;
		WritebackTmp = r.WritebackTmp;
		CommitLimit = r.CommitLimit;
		Committed_AS = r.Committed_AS;
		VmallocTotal = r.VmallocTotal;
		VmallocUsed = r.VmallocUsed;
		VmallocChunk = r.VmallocChunk;
		HardwareCorrupted = r.HardwareCorrupted;
		AnonHugePages = r.AnonHugePages;
		HugePages_Total = r.HugePages_Total;
		HugePages_Free = r.HugePages_Free;
		HugePages_Rsvd = r.HugePages_Rsvd;
		HugePages_Surp = r.HugePages_Surp;
		Hugepagesize = r.Hugepagesize;
		DirectMap4k = r.DirectMap4k;
		DirectMap2M = r.DirectMap2M;
		DirectMap1G = r.DirectMap1G;
		nr_dirty = r.nr_dirty;
		pswpin = r.pswpin;
		pswpout = r.pswpout;
		pgmajfault = r.pgmajfault;
		allocstall = r.allocstall;

	}
	public static SystemMemoryRecord produceRecord(){
		RandomAccessFile proc_meminfo = null, proc_vmstat = null;
		SystemMemoryRecord record = null;
		try {
			proc_meminfo = new RandomAccessFile("/proc/meminfo", "r");
			proc_vmstat = new RandomAccessFile("/proc/vmstat", "r");
		} catch (Exception e) {
			System.err.println("Failed to parse a file under /proc/");
			e.printStackTrace();
			return null;
		}
		record = produceRecord(proc_meminfo, proc_vmstat);
		try {
			proc_meminfo.close();
		} catch (Exception e) {}
		try {
			proc_vmstat.close();
		} catch (Exception e) {}
		return record;
	}
	public static SystemMemoryRecord produceRecord(RandomAccessFile proc_meminfo, RandomAccessFile proc_vmstat){
		SystemMemoryRecord newrecord = new SystemMemoryRecord();
		String[] parts;
		String line;
		
		newrecord.timestamp = System.currentTimeMillis();
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
							return null;
						}
					} else if (parts.length > 3) {
						System.err.println("Expecting lines with 2 or 3 fields in /proc/meminfo but found " + parts.length);
						return null;
					}
					newrecord.setValueByName(parts[0].split(":")[0], val);
				} else {
					System.err.println("Expecting lines with 2 or 3 fields in /proc/meminfo but found " + parts.length);
					return null;
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
					return null;
				}
			}
		} catch (Exception e) {	
			System.err.println("Caught an exception while processing /proc/meminfo");
			e.printStackTrace();
			return null;
		}
		return newrecord;
	}
	public static String[] type1DiffSupport(){
		return new String[] {	"SystemMemoryRecord.pswpin",
								"SystemMemoryRecord.pswpout",
								"SystemMemoryRecord.pgmajfault",
								"SystemMemoryRecord.allocstall"		};
	}
	public static SystemMemoryRecord type1Diff(SystemMemoryRecord oldRecord, SystemMemoryRecord newRecord){
		SystemMemoryRecord diffRecord = new SystemMemoryRecord();
		return type1Diff(oldRecord, newRecord, diffRecord);
	}
	public static SystemMemoryRecord type1Diff(SystemMemoryRecord oldRecord, SystemMemoryRecord newRecord, SystemMemoryRecord diffRecord){
		/**
		 * Not sure if we need this check....
		 */
		//if(oldRecord.timestamp >= newRecord.timestamp){
		//	return null;
		//}
		Record.type1Diff(oldRecord, newRecord, diffRecord);
		diffRecord.pswpin = newRecord.pswpin.subtract(oldRecord.pswpin);
		diffRecord.pswpout = newRecord.pswpout.subtract(oldRecord.pswpout);
		diffRecord.pgmajfault = newRecord.pgmajfault.subtract(oldRecord.pgmajfault);
		diffRecord.allocstall = newRecord.allocstall.subtract(oldRecord.allocstall);
		return diffRecord;
	}
	public static String[] type2DiffSupport(){
		return new String[] {	"SystemMemoryRecord.freeMemPct",
								"SystemMemoryRecord.MemFree"		};
	}
	public static SystemMemoryRecord type2Diff(SystemMemoryRecord[] records){
		SystemMemoryRecord diffRecord = new SystemMemoryRecord();
		return type2Diff(records, diffRecord);
	}
	public static SystemMemoryRecord type2Diff(SystemMemoryRecord[] records, SystemMemoryRecord diffRecord){
		if(records.length < 2) return null;
		Record.type2Diff(records, diffRecord);
		diffRecord.MemFree = new BigInteger("0");
		for (int x=1; x<records.length; x++) {
			/**
			 * Not sure if we need this check....
			 */
			//if(records[x-1].timestamp >= records[x].timestamp){
			//	return null;
			//}
			BigInteger elapsedTimems = new BigInteger(String.valueOf(records[x].timestamp - records[x-1].timestamp));
			diffRecord.MemFree = diffRecord.MemFree.add(records[x].MemFree.divide(elapsedTimems.divide(new BigInteger("1000"))));
		}
		
		diffRecord.freeMemPct = 100d * diffRecord.MemFree.divide(records[0].MemTotal).doubleValue();
		return diffRecord;
	}
	public boolean isBelowThreshold(String metricStr, double val){
		if(metricStr.equals("SystemMemoryRecord.freeMemPct"))
			return freeMemPct < val;
		return false;
	}
	public boolean isBelowThreshold(String metricStr, BigInteger val){
		if(metricStr.equals("SystemMemoryRecord.MemFree") && MemFree.compareTo(val) == -1)
			return true;
		return false;
	}
	public boolean isAboveThreshold(String metricStr, BigInteger val){
		if(metricStr.equals("SystemMemoryRecord.pswpin") && pswpin.compareTo(val) == 1)
			return true;
		else if(metricStr.equals("SystemMemoryRecord.pswpout") && pswpout.compareTo(val) == 1)
			return true;
		else if(metricStr.equals("SystemMemoryRecord.pgmajfault") && pgmajfault.compareTo(val) == 1)
			return true;
		else if(metricStr.equals("SystemMemoryRecord.allocstall") && allocstall.compareTo(val) == 1)
			return true;
		return false;
	}	
 	public boolean setValueByName(String name, BigInteger value){
		if (name.equals("MemTotal")) { MemTotal = value; }
		else if (name.equals("MemFree")) { MemFree = value; }
		else if (name.equals("Buffers")) { Buffers = value; }
		else if (name.equals("Cached")) { Cached = value; }
		else if (name.equals("SwapCached")) { SwapCached = value; }
		else if (name.equals("Active")) { Active = value; }
		else if (name.equals("Inactive")) { Inactive = value; }
		else if (name.equals("Active(anon)")) { Active_anon_ = value; }
		else if (name.equals("Inactive(anon)")) { Inactive_anon_ = value; }
		else if (name.equals("Active(file)")) { Active_file_ = value; }
		else if (name.equals("Inactive(file)")) { Inactive_file_ = value; }
		else if (name.equals("Unevictable")) { Unevictable = value; }
		else if (name.equals("Mlocked")) { Mlocked = value; }
		else if (name.equals("SwapTotal")) { SwapTotal = value; }
		else if (name.equals("SwapFree")) { SwapFree = value; }
		else if (name.equals("Dirty")) { Dirty = value; }
		else if (name.equals("Writeback")) { Writeback = value; }
		else if (name.equals("AnonPages")) { AnonPages = value; }
		else if (name.equals("Mapped")) { Mapped = value; }
		else if (name.equals("Shmem")) { Shmem = value; }
		else if (name.equals("Slab")) { Slab = value; }
		else if (name.equals("SReclaimable")) { SReclaimable = value; }
		else if (name.equals("SUnreclaim")) { SUnreclaim = value; }
		else if (name.equals("KernelStack")) { KernelStack = value; }
		else if (name.equals("PageTables")) { PageTables = value; }
		else if (name.equals("NFS_Unstable")) { NFS_Unstable = value; }
		else if (name.equals("Bounce")) { Bounce = value; }
		else if (name.equals("WritebackTmp")) { WritebackTmp = value; }
		else if (name.equals("CommitLimit")) { CommitLimit = value; }
		else if (name.equals("Committed_AS")) { Committed_AS = value; }
		else if (name.equals("VmallocTotal")) { VmallocTotal = value; }
		else if (name.equals("VmallocUsed")) { VmallocUsed = value; }
		else if (name.equals("VmallocChunk")) { VmallocChunk = value; }
		else if (name.equals("HardwareCorrupted")) { HardwareCorrupted = value; }
		else if (name.equals("AnonHugePages")) { AnonHugePages = value; }
		else if (name.equals("HugePages_Total")) { HugePages_Total = value; }
		else if (name.equals("HugePages_Free")) { HugePages_Free = value; }
		else if (name.equals("HugePages_Rsvd")) { HugePages_Rsvd = value; }
		else if (name.equals("HugePages_Surp")) { HugePages_Surp = value; }
		else if (name.equals("Hugepagesize")) { Hugepagesize = value; }
		else if (name.equals("DirectMap4k")) { DirectMap4k = value; }
		else if (name.equals("DirectMap2M")) { DirectMap2M = value; }
		else if (name.equals("DirectMap1G")) { DirectMap1G = value; }
		else if (name.equals("nr_dirty")) { nr_dirty = value; }
		else if (name.equals("pswpin")) { pswpin = value; }
		else if (name.equals("pswpout")) { pswpout = value; }
		else if (name.equals("pgmajfault")) { pgmajfault = value; }
		else if (name.equals("allocstall")) { allocstall = value; }

		else { return false; }
		return true;
	}
	public static SystemMemoryRecord diff(SystemMemoryRecord oldrec, SystemMemoryRecord newrec) {
		SystemMemoryRecord diffrec = new SystemMemoryRecord();
		diffrec.MemTotal = newrec.MemTotal;
		diffrec.MemFree = newrec.MemFree;
		diffrec.Buffers = newrec.Buffers;
		diffrec.Cached = newrec.Cached;
		diffrec.SwapCached = newrec.SwapCached;
		diffrec.Active = newrec.Active;
		diffrec.Inactive = newrec.Inactive;
		diffrec.Active_anon_ = newrec.Active_anon_;
		diffrec.Inactive_anon_ = newrec.Inactive_anon_;
		diffrec.Active_file_ = newrec.Active_file_;
		diffrec.Inactive_file_ = newrec.Inactive_file_;
		diffrec.Unevictable = newrec.Unevictable;
		diffrec.Mlocked = newrec.Mlocked;
		diffrec.SwapTotal = newrec.SwapTotal;
		diffrec.SwapFree = newrec.SwapFree;
		diffrec.Dirty = newrec.Dirty;
		diffrec.Writeback = newrec.Writeback;
		diffrec.AnonPages = newrec.AnonPages;
		diffrec.Mapped = newrec.Mapped;
		diffrec.Shmem = newrec.Shmem;
		diffrec.Slab = newrec.Slab;
		diffrec.SReclaimable = newrec.SReclaimable;
		diffrec.SUnreclaim = newrec.SUnreclaim;
		diffrec.KernelStack = newrec.KernelStack;
		diffrec.PageTables = newrec.PageTables;
		diffrec.NFS_Unstable = newrec.NFS_Unstable;
		diffrec.Bounce = newrec.Bounce;
		diffrec.WritebackTmp = newrec.WritebackTmp;
		diffrec.CommitLimit = newrec.CommitLimit;
		diffrec.Committed_AS = newrec.Committed_AS;
		diffrec.VmallocTotal = newrec.VmallocTotal;
		diffrec.VmallocUsed = newrec.VmallocUsed;
		diffrec.VmallocChunk = newrec.VmallocChunk;
		diffrec.HardwareCorrupted = newrec.HardwareCorrupted;
		diffrec.AnonHugePages = newrec.AnonHugePages;
		diffrec.HugePages_Total = newrec.HugePages_Total;
		diffrec.HugePages_Free = newrec.HugePages_Free;
		diffrec.HugePages_Rsvd = newrec.HugePages_Rsvd;
		diffrec.HugePages_Surp = newrec.HugePages_Surp;
		diffrec.Hugepagesize = newrec.Hugepagesize;
		diffrec.DirectMap4k = newrec.DirectMap4k;
		diffrec.DirectMap2M = newrec.DirectMap2M;
		diffrec.DirectMap1G = newrec.DirectMap1G;
		
		diffrec.nr_dirty = newrec.nr_dirty;
		diffrec.pswpin = newrec.pswpin.subtract(oldrec.pswpin);
		diffrec.pswpout = newrec.pswpout.subtract(oldrec.pswpout);
		diffrec.pgmajfault = newrec.pgmajfault.subtract(oldrec.pgmajfault);
		diffrec.allocstall = newrec.allocstall.subtract(oldrec.allocstall);
		
		diffrec.timestamp = newrec.timestamp;
		diffrec.previousTimestamp = oldrec.timestamp;
		diffrec.durationms = diffrec.timestamp - diffrec.previousTimestamp;
		//System.err.println("Generated memory.system record: " + diffrec.toString());
		return diffrec;
	}
	public String toString(){
		return super.toString() + " memory.system total:" + MemTotal + " free:" + MemFree;
	}
}
