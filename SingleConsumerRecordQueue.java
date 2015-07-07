import java.util.concurrent.LinkedBlockingQueue;
import java.util.Iterator;

public class SingleConsumerRecordQueue extends RecordQueue{
	private static Object lock = new Object();
	LinkedBlockingQueue<Record> queue = null;
	boolean debugEnabled=false;
	
	SingleConsumerRecordQueue(){};
	
	SingleConsumerRecordQueue(String id, int maxQueueLength) {
		this.id = id + ":RecQ";
		this.queue = new LinkedBlockingQueue<Record>(maxQueueLength);
		this.maxQueueLength = maxQueueLength;
	}
	
	public void setDebug(boolean debug){
		debugEnabled = debug;
	}
	
	public int queueSize() {
		synchronized(lock){
			return queue.size();
		}
	}
	
	public String printRecords() {
		String records = "";
		synchronized(lock){
			Iterator<Record> i = queue.iterator();
			while(i.hasNext()){
				records = i.next().toString() + "\n";
			}
		}
		return records;
	}
	
	public String printNewestRecords(int numRecords) {
		String records = "";
		synchronized(lock){
			if(numRecords > queue.size()) {
				numRecords = queue.size();
			}
			Iterator<Record> i = queue.iterator();
			for (int x=0; x<(queue.size() - numRecords); x++){
				i.next();
			}
			for (int x=0; x<numRecords; x++) {
				records = records + i.next().toString() + "\n";
			}
		}
		return records;
	}
	
	public Record[] dumpAllRecords() {
		synchronized(lock) {
			Record[] outputRecords = new Record[queue.size()];
			Iterator<Record> i = queue.iterator();
			int x=0;
			while(i.hasNext()){
				outputRecords[x] = i.next();
				x++;
			}
			return outputRecords;
		}
	}
	
	public Record[] dumpOldestRecords(int numRecords){
		synchronized(lock) {
			if(numRecords > queue.size()){
				numRecords = queue.size();
			}
			Record[] outputRecords = new Record[numRecords];
			Iterator<Record> i = queue.iterator();
			for(int x=0; x<numRecords; x++){
				outputRecords[x] = i.next();
			}
			return outputRecords;
		}
	}
	
	public Record[] dumpNewestRecords(int numRecords){
		synchronized(lock) {
			if(numRecords > queue.size()){
				numRecords = queue.size();
			}
			Record[] outputRecords = new Record[numRecords];
			Iterator<Record> i = queue.iterator();
			for(int x=0; x<(queue.size() - numRecords); x++){
				i.next();
			}
			for(int x=0; x<numRecords; x++){
				outputRecords[x] = i.next();
			}
			return outputRecords;
		}
	}
	
	public Record[] dumpRecordsFromTimeRange(long startTime, long endTime){
		synchronized(lock) {
			Iterator<Record> i = queue.iterator();
			boolean foundStart = true, foundEnd = true;
			int startPos=-1, endPos=-1;
			for(int x=0; x<queue.size(); x++){
				Record r = i.next();
				if(!foundStart && r.timestamp > startTime && r.timestamp <= endTime){
					foundStart = true;
					startPos = x;
				} else if (foundStart && r.timestamp > endTime) {
					foundEnd = true;
					endPos = x - 1;
					break;
				}
			}
			if(foundStart && !foundEnd){
				endPos = queue.size() - 1;
			}
			if(foundStart){
				Record[] outputRecords = new Record[endPos - startPos + 1];
				i = queue.iterator();
				for(int x=0; x<startPos; x++){
					i.next();
				}
				for(int x=0; x<(endPos - startPos + 1); x++){
					outputRecords[x] = i.next();
				}
				return outputRecords;
			}
		}
		return null;
	}
	
	

	public boolean put(Record record) {
		synchronized(lock){
			try {
				if (!queue.offer(record)) {
					return false;
				}
			} catch (Exception e) {
				System.err.println("DEBUG: " + id + ": Caught an exception attempting to insert record to queue");
				e.printStackTrace();
				return false;
			}
			return true;
		}
		
	}
	
	public Record get() {
		boolean getComplete = false;
		int sleepTime = 10;
		while(!getComplete){
			synchronized(lock){
				if(queue.size() > 0){
					try {
						return (Record) queue.take();
					} catch (Exception e) {
						System.err.println("DEBUG: " + id + ": Failed to take a record from queue");
						//e.printStackTrace();
					}
					System.err.println("Failed to take record from queue " + queue + " with size " + queue.size() + " so returning null");
					return null;
				}
			}
			if(!getComplete){
				try {Thread.sleep(sleepTime);}catch(Exception e){};
				if(sleepTime<1000) sleepTime = sleepTime * 10;
			}
		}
		return null;
	}
	
	public Record get(String name) {
		return get();
	}
	
}
