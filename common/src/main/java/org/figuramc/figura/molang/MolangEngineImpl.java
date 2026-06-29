package org.figuramc.figura.molang;

import org.figuramc.figura.molang.parser.CommentStripper;
import org.figuramc.figura.molang.parser.MolangParser;
import org.figuramc.figura.molang.parser.ParseException;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Default MolangEngine implementation.
 * Delegates parsing to MolangParser with the configured bindings.
 * Automatically strips comments before parsing.
 *
 * Reference: YSM MolangEngineImpl
 */
public final class MolangEngineImpl implements MolangEngine {

    private final ObjectBinding bindings;

    MolangEngineImpl(ObjectBinding bindings) {
        this.bindings = bindings;
    }

    @Override
    public List<Expression> parse(Reader reader) throws IOException {
        // Read the full input, strip comments, then parse
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        String cleaned = CommentStripper.stripComments(sb.toString());
        return MolangParser.parser(new StringReader(cleaned), this.bindings).parseAll();
    }

    @Override
    public List<Expression> parse(String str) throws ParseException {
        String cleaned = CommentStripper.stripComments(str);
        return MolangEngine.super.parse(cleaned);
    }
}
