/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.it;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.xwiki.validator.ValidationError;
import org.xwiki.validator.Validator;

import com.xpn.xwiki.it.framework.AbstractValidationTest;
import com.xpn.xwiki.it.framework.Target;

/**
 * Verifies that all pages in the default wiki are valid XHTML documents.
 * 
 * @version $Id$
 */
public class RSSValidationTest extends AbstractValidationTest
{
    private Validator validator;

    /**
     * We save the stdout stream since we replace it with our own in order to verify that XWiki doesn't generated any
     * error while validating documents and we fail the build if it does.
     */
    protected PrintStream stdout;

    /**
     * The new stdout stream we're using to replace the default console output.
     */
    protected ByteArrayOutputStream out;

    /**
     * We save the stderr stream since we replace it with our own in order to verify that XWiki doesn't generated any
     * error while validating documents and we fail the build if it does.
     */
    protected PrintStream stderr;

    /**
     * The new stderr stream we're using to replace the default console output.
     */
    protected ByteArrayOutputStream err;

    public RSSValidationTest(Target target, HttpClient client, Validator validator, String credentials)
        throws Exception
    {
        super("testDocumentRSSValidity", target, client, credentials);

        this.validator = validator;
    }

    public static Test suite(Class< ? extends AbstractValidationTest> validationTest, Validator validator)
        throws Exception
    {
        TestSuite suite = new TestSuite();

        HttpClient adminClient = new HttpClient();
        Credentials defaultcreds = new UsernamePasswordCredentials("Admin", "admin");
        adminClient.getState().setCredentials(AuthScope.ANY, defaultcreds);

        addRSSURLsForAdmin(validationTest, validator, suite, adminClient);

        HttpClient guestClient = new HttpClient();

        addRSSURLsForGuest(validationTest, validator, suite, guestClient);

        return suite;
    }

    private static void addRSSURLsForGuest(Class< ? extends AbstractValidationTest> validationTest,
        Validator validator, TestSuite suite, HttpClient client) throws Exception
    {
        addRSSURLs("rssUrlsToTestAsAdmin", validationTest, validator, suite, client, "Admin:admin");
    }

    private static void addRSSURLsForAdmin(Class< ? extends AbstractValidationTest> validationTest,
        Validator validator, TestSuite suite, HttpClient client) throws Exception
    {
        addRSSURLs("rssUrlsToTestAsGuest", validationTest, validator, suite, client, null);
    }

    public static void addRSSURLs(String property, Class< ? extends AbstractValidationTest> validationTest,
        Validator validator, TestSuite suite, HttpClient client, String credentials) throws Exception
    {
        addURLs(property, validationTest, validator, suite, client, credentials);
    }

    /**
     * {@inheritDoc}
     * 
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        super.setUp();

        // TODO Until we find a way to incrementally display the result of tests this stays
        System.out.println(getName());

        // We redirect the stdout and the stderr in order to detect (server-side) error/warning
        // messages like the ones generated by the velocity parser
        this.stdout = System.out;
        this.out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(this.out));
        this.stderr = System.err;
        this.err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(this.err));
    }

    /**
     * {@inheritDoc}
     * 
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception
    {
        // Restore original stdout and stderr streams.
        String output = this.out.toString();
        String errput = this.err.toString();

        System.setOut(this.stdout);
        System.out.print(output);
        System.setErr(this.stderr);
        System.err.print(errput);

        // Detect server-side error/warning messages from the stdout
        assertFalse("Errors found in the stdout output", hasLogErrors(output));
        assertFalse("Warnings found in the stdout output", hasLogWarnings(output));

        // Detect server-side error/warning messages from the stderr
        assertFalse("Errors found in the stderr output", hasLogErrors(errput));
        assertFalse("Warnings found in the stderr output", hasLogWarnings(errput));

        super.tearDown();
    }

    /**
     * {@inheritDoc}
     * 
     * @see junit.framework.TestCase#getName()
     */
    public String getName()
    {
        return "Validating " + this.validator.getName() + " validity for: " + this.target.getName();
    }

    public void testDocumentValidity() throws Exception
    {
        byte[] responseBody = getResponseBody();

        this.validator.setDocument(new ByteArrayInputStream(responseBody));
        List<ValidationError> errors = this.validator.validate();

        StringBuffer message = new StringBuffer();
        message.append("Validation errors in " + this.target.getName());
        boolean hasError = false;
        for (ValidationError error : errors) {
            if (error.getType() == ValidationError.Type.WARNING) {
                if (error.getLine() >= 0) {
                    System.out.println("Warning at " + error.getLine() + ":" + error.getColumn() + " "
                        + error.getMessage());
                } else {
                    System.out.println("Warning " + error.getMessage());
                }
            } else {
                if (error.getLine() >= 0) {
                    message.append("\n" + error.toString() + " at line [" + error.getLine() + "] column ["
                        + error.getColumn() + "]");
                } else {
                    message.append("\n" + error.toString());
                }

                hasError = true;
            }
        }

        if (hasError) {
            System.err.println("");
            System.err.println("Validated content:");
            BufferedReader reader = new BufferedReader(new StringReader(new String(responseBody)));
            int index = 1;
            for (String line = reader.readLine(); line != null; line = reader.readLine(), ++index) {
                System.err.println(index + "\t" + line);
            }
        }

        assertFalse(message.toString(), hasError);
    }

    protected boolean hasLogErrors(String output)
    {
        return output.indexOf("ERROR") >= 0 || output.indexOf("ERR") >= 0;
    }

    protected boolean hasLogWarnings(String output)
    {
        return output.indexOf("WARNING") >= 0 || output.indexOf("WARN") >= 0;
    }
}
