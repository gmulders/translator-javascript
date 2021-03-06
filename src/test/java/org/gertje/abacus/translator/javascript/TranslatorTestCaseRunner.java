package org.gertje.abacus.translator.javascript;

import org.gertje.abacus.AbacusTestCase;
import org.gertje.abacus.AbstractTestCaseRunner;
import org.gertje.abacus.context.AbacusContext;
import org.gertje.abacus.context.SimpleAbacusContext;
import org.gertje.abacus.exception.CompilerException;
import org.gertje.abacus.lexer.AbacusLexer;
import org.gertje.abacus.lexer.Lexer;
import org.gertje.abacus.nodes.AbacusNodeFactory;
import org.gertje.abacus.nodes.Node;
import org.gertje.abacus.nodes.NodeFactory;
import org.gertje.abacus.nodevisitors.SemanticsChecker;
import org.gertje.abacus.nodevisitors.Simplifier;
import org.gertje.abacus.nodevisitors.VisitingException;
import org.gertje.abacus.parser.Parser;
import org.gertje.abacus.symboltable.SymbolTable;
import org.gertje.abacus.translator.javascript.nodevisitors.JavaScriptTranslator;
import org.gertje.abacus.types.Type;
import org.junit.Assert;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Runs the test case for the JavaScript translator.
 */
public class TranslatorTestCaseRunner extends AbstractTestCaseRunner {

	@Override
	public void runTestCase() {
		// Maak een nieuwe symboltable en vul deze met wat waarden.
		SymbolTable sym = createSymbolTable();

		NodeFactory nodeFactory = new AbacusNodeFactory();

		Lexer lexer = new AbacusLexer(abacusTestCase.expression);
		Parser parser = new Parser(lexer, nodeFactory);

		Node tree;
		try {
			tree = parser.parse();
		} catch (CompilerException e) {
			if (!abacusTestCase.failsWithException) {
				Assert.fail(createMessage("Unexpected exception.", e));
			}
			return;
		}

		AbacusContext abacusContext = new SimpleAbacusContext(sym);
		SemanticsChecker semanticsChecker = new SemanticsChecker(sym);
		Simplifier simplifier = new Simplifier(abacusContext, nodeFactory);
		JavaScriptTranslator translator = new JavaScriptTranslator();

		String javascript;
		try {
			semanticsChecker.check(tree);

			if (!checkReturnType(tree.getType())) {
				Assert.fail(createMessage("Incorrect return type."));
			}

			tree = simplifier.simplify(tree);

			javascript = translator.translate(tree);
		} catch (VisitingException e) {
			if (!abacusTestCase.failsWithException) {
				Assert.fail(createMessage("Unexpected exception.", e));
			}
			return;
		}

		if (abacusTestCase.failsWithException && !abacusTestCase.succeedsInInterpreter) {
			Assert.fail(createMessage("Expected exception, but none was thrown."));
		}

		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		ScriptEngine nashorn = scriptEngineManager.getEngineByName("nashorn");

		try {
			nashorn.eval(createJavaScript(javascript));
		} catch (ScriptException e) {
			Assert.fail(createMessage(e.getMessage()));
		}

		boolean error = (Boolean)nashorn.get("error");
		String message = (String)nashorn.get("message");

		if (error) {
			System.out.println(javascript);
			Assert.fail(createMessage(message));
		}
	}

	/**
	 * Creates a piece of JavaScript that runs the expression and checks if the returned value is correct.
	 * @param expression The expression to run.
	 * @return JavaScipt script.
	 */
	private String createJavaScript(String expression) {
		return
				"var rand = Math.random;\n" +
				"var error = false;\n" +
				"var message = '';\n" +
				"\n" +
				createJavaScriptForSymbolTable() +
				"\n" +
				"var returnValue = " + expression + ";\n" +
				"\n" +
				"if (returnValue !== " + formatValueForJavaScript(abacusTestCase.returnValue.value, abacusTestCase.returnValue.type) + ") {\n" +
				"\terror = true;\n" +
				"\tmessage = 'Incorrect return value; ' + returnValue;\n" +
				"}\n" +
				"\n" +
				createJavaScriptForCheckSymbolTable();
	}

	/**
	 * Creates a piece of JavaScript that declares and initializes all variables from the symbol table.
	 * @return a piece of JavaScript that declares and initializes all variables from the symbol table.
	 */
	private String createJavaScriptForSymbolTable() {
		if (abacusTestCase.variableListBefore == null) {
			return "";
		}

		StringBuilder builder = new StringBuilder();

		for (AbacusTestCase.Value value : abacusTestCase.variableListBefore) {
			builder.append("var ").append(value.name).append(" = ")
					.append(formatValueForJavaScript(value.value, value.type))
					.append(";\n");
		}

		return builder.toString();
	}

	/**
	 * Creates a piece of JavaScript that checks whether the (JavaScript) variables have the correct values after
	 * running the expression.
	 * @return a piece of JavaScript that checks whether the (JavaScript) variables have the correct values after
	 * running the expression.
	 */
	private String createJavaScriptForCheckSymbolTable() {
		if (abacusTestCase.variableListAfter == null) {
			return "";
		}

		StringBuilder builder = new StringBuilder();

		for (AbacusTestCase.Value value : abacusTestCase.variableListAfter) {
			builder.append("if (").append(value.name).append(" !== ")
						.append(formatValueForJavaScript(value.value, value.type)).append(") {\n")
					.append("\terror = true;\n")
					.append("\tmessage = 'Incorrect value for ").append(value.name).append(":' + ").append(value.name)
						.append(";\n")
					.append("}\n");
		}

		return builder.toString();
	}

	/**
	 * Formats the value for JavaScript.
	 * @param value The value to be formatted.
	 * @param type The type of the value.
	 * @return The value formatted for JavaScript.
	 */
	private String formatValueForJavaScript(String value, Type type) {
		if (type == Type.STRING && value != null) {
			return "'" + value + "'";
		}
		return value;
	}
}
