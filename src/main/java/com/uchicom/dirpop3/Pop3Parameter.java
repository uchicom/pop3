/**
 * (c) 2014 uchicom
 */
package com.uchicom.dirpop3;

import java.io.File;
import java.io.PrintStream;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Pop3Parameter {
	/** メールボックスの基準フォルダ */
    private File base = Constants.DEFAULT_MAILBOX;;
	/** ホスト名 */
	private String hostName = "localhost";
	/** 待ち受けポート */
    private int port = Constants.DEFAULT_PORT;
	/** 受信する接続 (接続要求) のキューの最大長 */
    private int backlog = Constants.DEFAULT_BACK;
	/** プールするスレッド数 */
    private int pool = Constants.DEFAULT_POOL;
    /** 実行するサーバのタイプ */
    private String type = "single";

    private String[] args;
    public Pop3Parameter(String[] args) {
        this.args = args;
    }

    /**
     * 初期化
     * @param ps
     * @return
     */
    public boolean init(PrintStream ps) {
    	for (int i = 0; i < args.length - 1; i++) {
			switch (args[i]) {
			case "-type":// サーバタイプ
				this.type = args[++i];
				break;
			case "-dir":// メールフォルダ格納フォルダ
				base = new File(args[++i]);
				if (!base.exists() || !base.isDirectory()) {
					ps.println("mailbox directory is not found.");
					return false;
				}
				break;
			case "-host":// ホスト名
				hostName = args[++i];
				break;
			case "-port":// ポート
				port = Integer.parseInt(args[++i]);
				break;
			case "-back":// 接続待ち数
				backlog = Integer.parseInt(args[++i]);
				break;
			case "-pool":// スレッドプール数
				pool = Integer.parseInt(args[++i]);
				break;
			}
		}

        return true;
    }

    public String getHostName() {
        return hostName;
    }

    public int getPort() {
        return port;
    }
    public int getBacklog() {
        return backlog;
    }
    public File getBase() {
        return base;
    }
    public int getPool() {
        return pool;
    }

    public Server createServer() {
    	Server server = null;
		switch (type) {
		case "multi":
			server = new MultiPop3Server(this);
		case "pool":
			server = new PoolPop3Server(this);
			break;
		case "single":
			server = new SinglePop3Server(this);
			break;
		case "selector":
			server = new SelectorPop3Server(this);
			break;
		}
    	return server;
    }
}
