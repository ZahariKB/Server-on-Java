package laba_10_2;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.xml.sax.SAXException;
import java.io.File;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class ValidateXML {
    public static void main(String[] args) {
        try {
            File xmlFile = new File("requests.xml");

         
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(true);
            factory.setNamespaceAware(true);

          
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler()); // Обработка ошибок

           
            builder.parse(xmlFile);

            System.out.println("XML-документ валиден.");
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }
}
