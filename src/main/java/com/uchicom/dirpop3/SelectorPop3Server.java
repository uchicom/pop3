/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Channelを利用したPOP3サーバー
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SelectorPop3Server extends AbstractSelectorServer {


	protected static Queue<ServerSocketChannel> serverQueue = new ConcurrentLinkedQueue<ServerSocketChannel>();
	/**
	 * @param parameter
	 */
	public SelectorPop3Server(Pop3Parameter parameter) {
		super(parameter);
	}

	private static boolean alive = true;

	/**
	 * メイン処理
	 *
	 */
	@Override
	protected void execute(Selector selector) throws IOException{
		while (alive) {
			if (selector.select() > 0) {
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> ite = keys.iterator();
				while (ite.hasNext()) {
					SelectionKey key = ite.next();
					ite.remove();
					try {
						if (key.isValid()) {
							((Handler) key.attachment()).handle(key);
						} else {
							key.cancel();
						}
					} catch (IOException e) {
						e.printStackTrace();
						key.cancel();
					} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
						key.cancel();
					}
				}
			}
		}
	}


}
