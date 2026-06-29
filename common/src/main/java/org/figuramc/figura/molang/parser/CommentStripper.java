package org.figuramc.figura.molang.parser;

/**
 * Utility for stripping Molang comments before parsing.
 * Supports both single-line ({@code //}) and multi-line ({@code /* ... * /}) comments.
 *
 * <p>Molang timeline scripts in .bbmodel files often contain JavaScript-style comments.
 * The Molang lexer/parser does not natively handle comments, so they must be stripped
 * before tokenization.</p>
 *
 * Reference: YSM MolangParser.stripComments()
 */
public final class CommentStripper {

    private CommentStripper() {
    }

    /**
     * Strips Molang comments from the input string.
     * Handles:
     * <ul>
     *   <li>Single-line comments: {@code // ...}</li>
     *   <li>Multi-line comments: {@code /* ... * /}</li>
     *   <li>String literals (single quotes) are preserved and their contents
     *       are not treated as comments</li>
     * </ul>
     *
     * @param input The raw Molang expression string
     * @return The string with comments replaced by spaces
     */
    public static String stripComments(String input) {
        if (input == null || input.isEmpty()) return input;

        StringBuilder result = new StringBuilder(input.length());
        int i = 0;
        int len = input.length();

        while (i < len) {
            char c = input.charAt(i);

            // Handle string literals - preserve their contents
            if (c == '\'') {
                result.append(c);
                i++;
                while (i < len) {
                    c = input.charAt(i);
                    result.append(c);
                    i++;
                    if (c == '\'') break; // End of string
                }
                continue;
            }

            // Check for single-line comment: //
            if (c == '/' && i + 1 < len && input.charAt(i + 1) == '/') {
                // Skip until end of line
                while (i < len) {
                    c = input.charAt(i);
                    if (c == '\n') break;
                    // Replace comment content with space
                    result.append(' ');
                    i++;
                }
                continue;
            }

            // Check for multi-line comment: /* ... */
            if (c == '/' && i + 1 < len && input.charAt(i + 1) == '*') {
                result.append(' '); // Replace opening /* with space
                result.append(' ');
                i += 2;
                while (i < len) {
                    c = input.charAt(i);
                    if (c == '*' && i + 1 < len && input.charAt(i + 1) == '/') {
                        result.append(' '); // Replace closing */ with space
                        result.append(' ');
                        i += 2;
                        break;
                    }
                    result.append(' ');
                    i++;
                }
                continue;
            }

            // Normal character
            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * Convenience method: strips comments from the input, then parses it.
     */
    public static String stripAndParse(String input) {
        return stripComments(input);
    }
}
