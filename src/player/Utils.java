package player;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A collection of convenience methods ...
 * 
 * @author smduarte (smd@fct.unl.pt)
 * 
 */
final public class Utils {

	protected Utils() {
	}

	static public Thread newThread(boolean daemon, Runnable r) {
		Thread res = new Thread(r);
		res.setDaemon(daemon);
		return res;
	}

	static public void sleep(long ms) {
		try {
			if (ms > 0)
				Thread.sleep(ms);
		} catch (InterruptedException x) {
			x.printStackTrace();
		}
	}

	static public void sleep(long ms, int ns) {
		try {
			if (ms > 0 || ns > 0)
				Thread.sleep(ms, ns);
		} catch (InterruptedException x) {
			x.printStackTrace();
		}
	}

	static public void waitOn(Object o) {
		try {
			o.wait();
		} catch (InterruptedException x) {
			x.printStackTrace();
		}
	}

	static public void waitOn(Object o, long ms) {
		try {
			if (ms > 0)
				o.wait(ms);
		} catch (InterruptedException x) {
			x.printStackTrace();
		}
	}

	static public <T> T takeFrom(BlockingQueue<T> queue) {
		try {
			return (T) queue.take();
		} catch (InterruptedException e) {
		}
		return null;
	}

	static public <T> void putInto(BlockingQueue<T> queue, T elem) {
		try {
			queue.put(elem);
		} catch (InterruptedException e) {
		}
	}

	static public <T> T poll(BlockingQueue<T> queue, int timeout) {
		try {
			return (T) queue.poll(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
		}
		return null;
	}

	static public <T> void offer(BlockingQueue<T> queue, T val, int timeout) {
		try {
			queue.offer(val, timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
		}
	}

	static public <T> void add(BlockingQueue<T> queue, T val) {
		while (true) {
			try {
				queue.add(val);
			} catch (IllegalStateException x) {
			}
			return;
		}
	}

	static public void notifyOn(Object o) {
		o.notify();
	}

	static public void notifyAllOn(Object o) {
		o.notifyAll();
	}

	static public void synchronizedWaitOn(Object o) {
		synchronized (o) {
			try {
				o.wait();
			} catch (InterruptedException x) {
				x.printStackTrace();
			}
		}
	}

	static public void synchronizedWaitOn(Object o, long ms) {
		synchronized (o) {
			try {
				o.wait(ms);
			} catch (InterruptedException x) {
				x.printStackTrace();
			}
		}
	}

	static public void synchronizedNotifyOn(Object o) {
		synchronized (o) {
			o.notify();
		}
	}

	static public void synchronizedNotifyAllOn(Object o) {
		synchronized (o) {
			o.notifyAll();
		}
	}
}
