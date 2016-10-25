package com.uchicom.dirpop3;

import com.uchicom.server.Handler;
import com.uchicom.server.HandlerFactory;
import com.uchicom.server.Parameter;

public class Pop3HandlerFactory extends HandlerFactory {

	public Pop3HandlerFactory(Parameter parameter) {
		super(parameter);
	}

	@Override
	public Handler createHandler() {
		return new Pop3Handler(parameter.getFile("dir"), parameter.get("host"));
	}

}
