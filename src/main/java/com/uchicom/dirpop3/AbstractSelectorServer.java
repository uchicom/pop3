/**
 * (c) 2016 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * nio2を使用した抽象サーバクラス
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public abstract class AbstractSelectorServer implements Server {

	protected static Queue<ServerSocketChannel> serverQueue = new ConcurrentLinkedQueue<ServerSocketChannel>();
	Pop3Parameter parameter;

	protected List<Pop3Process> processList = new CopyOnWriteArrayList<Pop3Process>();

	public AbstractSelectorServer(Pop3Parameter parameter) {
		this.parameter = parameter;
	}

	/* (非 Javadoc)
	 * @see com.uchicom.dirpop3.Server#execute()
	 */
	@Override
	public void execute() {

		try (ServerSocketChannel server = ServerSocketChannel.open();) {
			server.socket().setReuseAddress(true);
			server.socket().bind(new InetSocketAddress(parameter.getPort()), parameter.getBacklog());
			server.configureBlocking(false);
			serverQueue.add(server);

			Selector selector = Selector.open();
			server.register(selector,
					SelectionKey.OP_ACCEPT,
					new AcceptHandler(parameter.getBase(), parameter.getHostName()));
			execute(selector);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	abstract protected void execute(Selector selector) throws IOException;

	public static void shutdown(String[] args) {
		if (!serverQueue.isEmpty()) {
			try {
				serverQueue.poll().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
