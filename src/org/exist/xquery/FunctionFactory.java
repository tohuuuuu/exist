/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.functions.ExtNear;
import org.exist.xquery.functions.ExtPhrase;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.Type;

public class FunctionFactory {

	/**
	 * Create a function call. 
	 * 
	 * This method handles all calls to built-in or user-defined
	 * functions. It also deals with constructor functions and
	 * optimizes some function calls like starts-with, ends-with or
	 * contains. 
	 */
	public static Expression createFunction(
		XQueryContext context,
		XQueryAST ast,
		PathExpr parent,
		List params)
		throws XPathException {
		QName qname = null;
		try {
			qname = QName.parse(context, ast.getText(), context.getDefaultFunctionNamespace());
		} catch(XPathException e) {
			e.setASTNode(ast);
			throw e;
		}
		String local = qname.getLocalName();
		String uri = qname.getNamespaceURI();
		Expression step = null;
		if(uri.equals(Module.BUILTIN_FUNCTION_NS)) {
			// near(node-set, string)
			if (local.equals("near")) {
				if (params.size() < 2)
					throw new XPathException(ast, "Function near requires two arguments");
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new XPathException(ast, "Second argument to near is empty");
				Expression e1 = p1.getExpression(0);
				ExtNear near = new ExtNear(context);
				near.setASTNode(ast);
				near.addTerm(e1);
				near.setPath((PathExpr) params.get(0));
				if (params.size() > 2) {
					p1 = (PathExpr) params.get(2);
					if (p1.getLength() == 0)
						throw new XPathException(ast, "Distance argument to near is empty");
					near.setDistance(p1);
				}
				step = near;
			}
	
			// phrase(node-set, string)
            if (local.equals("phrase")) {
                if (params.size() < 2)
                    throw new XPathException(ast, "Function phrase requires two arguments");
                PathExpr p1 = (PathExpr) params.get(1);
                if (p1.getLength() == 0)
                    throw new XPathException(ast, "Second argument to phrase is empty");   
                Expression e1 = p1.getExpression(0);
                ExtPhrase phrase = new ExtPhrase(context);
                phrase.setASTNode(ast);
                phrase.addTerm(e1);
                phrase.setPath((PathExpr) params.get(0));                                          step = phrase;
            }
			
			// starts-with(node-set, string)
			if (local.equals("starts-with")) {
				if (params.size() < 2)
					throw new XPathException(ast, "Function starts-with requires two arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new XPathException(ast, "Second argument to starts-with is empty");
				GeneralComparison op = 
					new GeneralComparison(context, p0, p1, Constants.EQ, Constants.TRUNC_RIGHT);
				op.setASTNode(ast);
				if (params.size() == 3)
					op.setCollation((Expression)params.get(2));
				step = op;
			}
	
			// ends-with(node-set, string)
			if (local.equals("ends-with")) {
				if (params.size() < 2)
					throw new XPathException(ast, "Function ends-with requires two arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new XPathException(ast, "Second argument to ends-with is empty");
				GeneralComparison op =
					new GeneralComparison(context, p0, p1, Constants.EQ, Constants.TRUNC_LEFT);
				op.setASTNode(ast);
				if (params.size() == 3)
					op.setCollation((Expression)params.get(2));
				step = op;
			}
	
			// contains(node-set, string)
			if (local.equals("contains")) {
				if (params.size() < 2)
					throw new XPathException(ast, "Function contains requires two arguments");
				PathExpr p0 = (PathExpr) params.get(0);
				PathExpr p1 = (PathExpr) params.get(1);
				if (p1.getLength() == 0)
					throw new XPathException(ast, "Second argument to contains is empty");
				GeneralComparison op =
					new GeneralComparison(context, p0, p1, Constants.EQ, Constants.TRUNC_BOTH);
				op.setASTNode(ast);
				if (params.size() == 3)
					op.setCollation((Expression)params.get(2));
				step = op;
			}
		// Check if the namespace belongs to one of the schema namespaces.
		// If yes, the function is a constructor function
		} else if(uri.equals(XQueryContext.SCHEMA_NS) || uri.equals(XQueryContext.XPATH_DATATYPES_NS)) {
			if(params.size() != 1)
				throw new XPathException(ast, "Wrong number of arguments for constructor function");
			PathExpr arg = (PathExpr)params.get(0);
			int code= Type.getType(qname);
			CastExpression castExpr = new CastExpression(context, arg, code, Cardinality.ZERO_OR_ONE);
			castExpr.setASTNode(ast);
			step = castExpr;
			
		// Check if the namespace URI starts with "java:". If yes, treat the function call as a call to
		// an arbitrary Java function.
		} else if(uri.startsWith("java:")) {
			JavaCall call = new JavaCall(context, qname);
			call.setASTNode(ast);
			call.setArguments(params);
			step = call;
		}
		
		// None of the above matched: function is either a builtin function or
		// a user-defined function 
		if (step == null) {
			Module module = context.getModule(uri);
			if(module != null) {
                // Function belongs to a module
				if(module.isInternalModule()) {
					// for internal modules: create a new function instance from the class
					FunctionDef def = ((InternalModule)module).getFunctionDef(qname, params.size());
					if (def == null)
						throw new XPathException(ast, "function " + qname.toString() + " ( namespace-uri = " + 
							qname.getNamespaceURI() + ") is not defined");
					Function func = Function.createFunction(context, ast, def );
					func.setArguments(params);
					step = func;
				} else {
                    // function is from an imported XQuery module
					UserDefinedFunction func = ((ExternalModule)module).getFunction(qname);
					if(func == null)
						throw new XPathException(ast, "function " + qname.toString() + " ( namespace-uri = " + 
							qname.getNamespaceURI() + ") is not defined");
					FunctionCall call = new FunctionCall(context, func);
					call.setArguments(params);
					call.setASTNode(ast);
					step = call;
				}
			} else {
				UserDefinedFunction func = context.resolveFunction(qname);
				FunctionCall call;
				if(func != null) {
					call = new FunctionCall(context, func);
					call.setASTNode(ast);
					call.setArguments(params);
				} else {
					// create a forward reference which will be resolved later
					call = new FunctionCall(context, qname, params);
					call.setASTNode(ast);
					context.addForwardReference(call);
				}
				step = call;
			}
		}
		return step;
	}
}
