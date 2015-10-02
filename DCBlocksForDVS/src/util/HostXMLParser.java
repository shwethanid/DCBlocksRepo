package util;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HostXMLParser {
	Document dom;
	String filename;

	public HostXMLParser(String filename) {
		this.filename = filename;
	}

	public ArrayList<HostNode> parseDocument() {
		ArrayList<HostNode> hostlist = new ArrayList<HostNode>();

		// Get the DOM Builder Factory
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// Get the DOM Builder
		DocumentBuilder builder = null;
		try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Load and Parse the XML document
		// document contains the complete XML as a Tree.
		Document document = null;
		try {
			document = builder.parse(ClassLoader
					.getSystemResourceAsStream(this.filename));
		} catch (SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Iterating through the nodes and extracting the data.
		NodeList nodeList = document.getDocumentElement().getChildNodes();
		// System.out.println("node list size: "+nodeList.getLength());
		for (int i = 0; i < nodeList.getLength(); i++) {
			// We have encountered an <employee> tag.
			Node node = (Node) nodeList.item(i);
			if (node instanceof Element) {
				HostNode host = new HostNode();
				// Fill the node id into HostNode object
				host.setNodeid(Integer.parseInt(node.getAttributes()
						.getNamedItem("id").getNodeValue()));
				// System.out.println("node id: "+host.getNodeid());
				NodeList childNodes = node.getChildNodes();
				for (int j = 0; j < childNodes.getLength(); j++) {
					Node cNode = (Node) childNodes.item(j);
					// Identifying the child tag of employee encountered.
					if (cNode instanceof Element) {
						String val = cNode.getLastChild().getTextContent()
								.trim();
						switch (cNode.getNodeName()) {
						case "Hostname":
							host.setHostname(val);
							break;
						case "Port":
							host.setPort(Integer.parseInt(val));
							break;

						}
					}
				}
				hostlist.add(host);
				
			}
		}
		return hostlist;
	}
}
