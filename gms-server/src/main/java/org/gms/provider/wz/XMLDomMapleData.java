/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gms.provider.wz;

import org.gms.constants.game.GameConstants;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import org.gms.provider.Data;
import org.gms.provider.DataEntity;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class XMLDomMapleData implements Data {
    /**
     * Xerces' DOM NodeList cache is not safe when different XMLDomMapleData
     * wrappers traverse the same document concurrently. Instance-level locks
     * do not help because every child creates a new wrapper instance.
     */
    private static final Object DOM_ACCESS_LOCK = new Object();
    private final Node node;
    private Path imageDataDir;

    public XMLDomMapleData(FileInputStream fis, Path imageDataDir) {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            // Some Talery exports contain raw control bytes in WZ string/name
            // attributes.  They are not legal in XML 1.0 and make the whole
            // DOM parse fail before the server can initialize.  Filter only
            // the XML 1.0-prohibited byte range at the boundary; do not
            // rewrite the imported WZ files or alter legal whitespace.
            Reader xmlReader = new Xml10SanitizingReader(new InputStreamReader(
                    new Xml10SanitizingInputStream(fis), StandardCharsets.UTF_8));
            Document document = documentBuilder.parse(new InputSource(xmlReader));
            this.node = document.getFirstChild();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.imageDataDir = imageDataDir;
    }

    /**
     * Removes byte values which cannot occur in an XML 1.0 document.
     *
     * The imported XML is UTF-8.  XML 1.0 permits TAB, LF and CR from the
     * ASCII control range; all other values below 0x20 are invalid.  UTF-8
     * continuation bytes are >= 0x80 and are therefore passed through
     * unchanged, preserving Chinese and other non-ASCII text.
     */
    private static final class Xml10SanitizingInputStream extends InputStream {
        private final InputStream delegate;

        private Xml10SanitizingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value;
            do {
                value = delegate.read();
            } while (isIllegalXml10Byte(value));
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }

            int count = 0;
            while (count < length) {
                int value = read();
                if (value < 0) {
                    return count == 0 ? -1 : count;
                }
                buffer[offset + count++] = (byte) value;
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private static boolean isIllegalXml10Byte(int value) {
            return value >= 0 && value < 0x20 && value != 0x09 && value != 0x0A && value != 0x0D;
        }
    }

    /**
     * Removes XML 1.0-invalid Unicode characters that are encoded as valid
     * UTF-8 sequences (for example U+FFFE).  The byte filter above handles
     * raw ASCII control bytes; this layer handles the decoded character set.
     */
    private static final class Xml10SanitizingReader extends Reader {
        private final Reader delegate;

        private Xml10SanitizingReader(Reader delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value;
            do {
                value = delegate.read();
            } while (isIllegalXml10Character(value));
            return value;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }

            int count = 0;
            while (count < length) {
                int value = read();
                if (value < 0) {
                    return count == 0 ? -1 : count;
                }
                buffer[offset + count++] = (char) value;
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private static boolean isIllegalXml10Character(int value) {
            return value >= 0 && ((value < 0x20 && value != 0x09 && value != 0x0A && value != 0x0D)
                    || value == 0xFFFE || value == 0xFFFF);
        }
    }

    private XMLDomMapleData(Node node) {
        this.node = node;
    }

    @Override
    public Data getChildByPath(String path) {  // the whole XML reading system seems susceptible to give nulls on strenuous read scenarios
        synchronized (DOM_ACCESS_LOCK) {
            String[] segments = path.split("/");
            if (segments[0].equals("..")) {
                DataEntity parent = getParent();
                return parent == null ? null : ((Data) parent).getChildByPath(path.substring(path.indexOf("/") + 1));
            }

            Node myNode = node;
            for (String s : segments) {
                NodeList childNodes = myNode.getChildNodes();
                boolean foundChild = false;
                for (int i = 0; i < childNodes.getLength(); i++) {
                    Node childNode = childNodes.item(i);
                    if (childNode == null || childNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }

                    NamedNodeMap attributes = childNode.getAttributes();
                    Node nameAttribute = attributes == null ? null : attributes.getNamedItem("name");
                    if (nameAttribute != null && nameAttribute.getNodeValue().equals(s)) {
                        myNode = childNode;
                        foundChild = true;
                        break;
                    }
                }
                if (!foundChild) {
                    return null;
                }
            }

            XMLDomMapleData ret = new XMLDomMapleData(myNode);
            ret.imageDataDir = imageDataDir.resolve(safePathComponent(getName())).resolve(path).getParent();
            return ret;
        }
    }

    @Override
    public List<Data> getChildren() {
        synchronized (DOM_ACCESS_LOCK) {
            List<Data> ret = new ArrayList<>();

            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                if (childNode != null && childNode.getNodeType() == Node.ELEMENT_NODE) {
                    XMLDomMapleData child = new XMLDomMapleData(childNode);
                    child.imageDataDir = imageDataDir.resolve(safePathComponent(getName()));
                    ret.add(child);
                }
            }

            return ret;
        }
    }

    @Override
    public synchronized Object getData() {
        NamedNodeMap attributes = node.getAttributes();
        DataType type = getType();
        switch (type) {
            case DOUBLE:
            case FLOAT:
            case INT:
            case SHORT: {
                String value = attributes.getNamedItem("value").getNodeValue();
                Number nval = GameConstants.parseNumber(value);

                switch (type) {
                    case DOUBLE:
                        return nval.doubleValue();
                    case FLOAT:
                        return nval.floatValue();
                    case INT:
                        return nval.intValue();
                    case SHORT:
                        return nval.shortValue();
                    default:
                        return null;
                }
            }
            case STRING:
            case UOL: {
                String value = attributes.getNamedItem("value").getNodeValue();
                return value;
            }
            case VECTOR: {
                String x = attributes.getNamedItem("x").getNodeValue();
                String y = attributes.getNamedItem("y").getNodeValue();
                return new Point(Integer.parseInt(x), Integer.parseInt(y));
            }
            case CANVAS: {
                String width = attributes.getNamedItem("width").getNodeValue();
                String height = attributes.getNamedItem("height").getNodeValue();
                return new Point(Integer.parseInt(width), Integer.parseInt(height));
            }
            default:
                return null;
        }
    }

    @Override
    public synchronized DataType getType() {
        String nodeName = node.getNodeName();

        switch (nodeName) {
            case "imgdir":
                return DataType.PROPERTY;
            case "canvas":
                return DataType.CANVAS;
            case "convex":
                return DataType.CONVEX;
            case "sound":
                return DataType.SOUND;
            case "uol":
                return DataType.UOL;
            case "double":
                return DataType.DOUBLE;
            case "float":
                return DataType.FLOAT;
            case "int":
                return DataType.INT;
            case "short":
                return DataType.SHORT;
            case "string":
                return DataType.STRING;
            case "vector":
                return DataType.VECTOR;
            case "null":
                return DataType.IMG_0x00;
        }
        return null;
    }

    @Override
    public synchronized DataEntity getParent() {
        Node parentNode;
        parentNode = node.getParentNode();
        if (parentNode.getNodeType() == Node.DOCUMENT_NODE) {
            return null;
        }
        XMLDomMapleData parentData = new XMLDomMapleData(parentNode);
        parentData.imageDataDir = imageDataDir.getParent();
        return parentData;
    }

    @Override
    public synchronized String getName() {
        return node.getAttributes().getNamedItem("name").getNodeValue();
    }

    /**
     * XML node names are data keys and must remain untouched for lookups.
     * They are also used by the legacy exporter as directory components for
     * optional canvas files, however, so keep that filesystem-only operation
     * safe on Windows when an imported name contains converter garbage.
     */
    private static String safePathComponent(String name) {
        if (name == null) {
            return "_";
        }

        String safe = name.trim().replaceAll("[\\x00-\\x1F<>:\"/\\\\|?*]", "");
        while (safe.endsWith(".") || safe.endsWith(" ")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) {
            return "_";
        }
        return safe;
    }

    @Override
    public synchronized Iterator<Data> iterator() {
        return getChildren().iterator();
    }

    /**
     * 获取指定节点属性值
     * @return
     */
    public synchronized String getAttributeValue(String name) {
        Node attr = node.getAttributes().getNamedItem(name);
        return attr == null ? null : attr.getNodeValue();
    }
}
