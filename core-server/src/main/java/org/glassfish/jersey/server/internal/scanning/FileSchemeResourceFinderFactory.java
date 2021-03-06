/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.jersey.server.internal.scanning;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

import org.glassfish.jersey.server.internal.AbstractResourceFinderAdapter;

/**
 * A "file" scheme URI scanner that recursively scans directories.
 * Files are reported to a {@link ResourceProcessor}.
 *
 * @author Paul Sandoz
 */
class FileSchemeResourceFinderFactory implements UriSchemeResourceFinderFactory {

    @Override
    public Set<String> getSchemes() {
        return Collections.singleton("file");
    }

    /**
     * Create new "file" scheme URI scanner factory.
     */
    FileSchemeResourceFinderFactory() {
    }

    @Override
    public FileSchemeScanner create(URI uri, boolean recursive) {
        return new FileSchemeScanner(uri, recursive);
    }

    private class FileSchemeScanner extends AbstractResourceFinderAdapter {

        private final ResourceFinderStack resourceFinderStack;
        private final boolean recursive;

        private FileSchemeScanner(final URI uri, boolean recursive) {
            this.resourceFinderStack = new ResourceFinderStack();
            this.recursive = recursive;

            processFile(new File(uri.getPath()));
        }

        @Override
        public boolean hasNext() {
            return resourceFinderStack.hasNext();
        }

        @Override
        public String next() {
            return resourceFinderStack.next();
        }

        @Override
        public InputStream open() {
            return resourceFinderStack.open();
        }

        @Override
        public void reset() {
            throw new UnsupportedOperationException();
        }

        private void processFile(final File f) {
            resourceFinderStack.push(new AbstractResourceFinderAdapter() {

                Stack<File> files = new Stack<File>() {{
                    if (f.isDirectory()) {
                        final File[] subDirFiles = f.listFiles();
                        if (subDirFiles != null) {
                            for (File file : subDirFiles) {
                                push(file);
                            }
                        }
                    } else {
                        push(f);
                    }
                }};

                private File current;
                private File next;

                @Override
                public boolean hasNext() {
                    while (next == null && !files.empty()) {
                        next = files.pop();

                        if (next.isDirectory()) {
                            if (recursive) {
                                processFile(next);
                            }
                            next = null;
                        }
                    }

                    return next != null;
                }

                @Override
                public String next() {
                    if (next != null || hasNext()) {
                        current = next;
                        next = null;
                        return current.getName();
                    }

                    throw new NoSuchElementException();
                }

                @Override
                public InputStream open() {
                    try {
                        return new FileInputStream(current);
                    } catch (FileNotFoundException e) {
                        throw new ResourceFinderException(e);
                    }
                }

                @Override
                public void reset() {
                    throw new UnsupportedOperationException();
                }
            });
        }
    }
}
