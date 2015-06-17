/**
 * The MIT License
 * Copyright (c) 2013 Pierre-Denis Vanduynslager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.syncthemall.enml4j.impl;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Resource;
import com.syncthemall.enml4j.converter.Converter;
import com.syncthemall.enml4j.converter.MediaConverter;
import com.syncthemall.enml4j.exception.MissingResourceException;
import com.syncthemall.enml4j.util.Elements;
import com.syncthemall.enml4j.util.Utils;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.syncthemall.enml4j.util.Constants.A;
import static com.syncthemall.enml4j.util.Constants.ALT;
import static com.syncthemall.enml4j.util.Constants.BASE64;
import static com.syncthemall.enml4j.util.Constants.DATA;
import static com.syncthemall.enml4j.util.Constants.HASH;
import static com.syncthemall.enml4j.util.Constants.HREF;
import static com.syncthemall.enml4j.util.Constants.IMAGE;
import static com.syncthemall.enml4j.util.Constants.IMG;
import static com.syncthemall.enml4j.util.Constants.SPAN;
import static com.syncthemall.enml4j.util.Constants.SRC;
import static com.syncthemall.enml4j.util.Constants.STYLE;
import static com.syncthemall.enml4j.util.Constants.TITLE;
import static com.syncthemall.enml4j.util.Constants.TYPE;

/**
 * Default {@code Converter} implementation to convert {@code <en-media>} ENML tags.
 * <p>
 * This {@link Converter} will replace an {@code <en-media>} tag by:<br>
 * <ul>
 * <li>an {@code <img></img>} HTML tag if it's contains an image,</li>
 * <li>an {@code <a></a>} HTML if it's contains an other type of file.</li>
 * </ul>
 * <br>
 * In the case of an non image type file an additional {@code <img></img>} tag will be inserted in the {@code <a></a>}
 * in order to display a link with an icon corresponding to the mime type of the file. This additional
 * {@code <img></img>} tag will uses URI scheme to encode image data.
 * <p>
 * For example if the {@code <en-media>} tag contains an image :
 * {@code <en-media width="640" height="480" type="image/jpeg" hash="f03c1c2d96bc67eda02968c8b5af9008"/>}<br>
 * will be replaced by :<br>
 * {@code <img width="640" height="480" type="image/jpeg" src="http://www.starkindustries.com/images/logo.png" alt="logo.png" />}
 * <p>
 * If the {@code <en-media>} tag contains a non image file :
 * {@code <en-media type="application/pdf" hash="f03c1c2d96bc67eda02968c8b5af9008"/>} will be replaced by :<br>
 * {@code <a href="http://www.starkindustries.com/private/Mark47.pdf" type="application/pdf" style="text-decoration: none;color: #6f6f6f;position: relative; display: block;">"}
 * <br>
 * {@code <img title="Mark47.pdf" type="application/pdf" style="position:absolute;border-color:transparent;" alt="" src="data:image/png;base64, #######" >}
 * <br>
 * {@code <span title="Mark47.pdf" style="display: block;line-height: 48px;margin-left: 56px;"> Mark47.pdf</span>}<br>
 * {@code </img>}<br>
 * {@code </a/>}
 * 
 * @see <a href="http://en.wikipedia.org/wiki/Data_URI_scheme">Data_URI_scheme</a>
 * @see <a href="http://dev.evernote.com/start/core/enml.php">Understanding the Evernote Markup Language</a>
 * @see <a href="http://docs.oracle.com/javaee/5/tutorial/doc/bnbdv.html">Streaming API for XML</a>
 * 
 * @author Pierre-Denis Vanduynslager pierre.denis.vanduynslager@gmail.com
 */
public class DefaultMediaTagConverter extends MediaConverter {

	/**
	 * Replace an {@code <en-media>} tag by an {@code <img></img>} or {@code <a></a>} tag.
	 */
	public final Elements convertElement(final StartElement start, final Note note, final Map<String, String> mapHashURL) {

		Attribute type = start.getAttributeByName(new QName(TYPE));
		Attribute hash = start.getAttributeByName(new QName(HASH));

		if (type.getValue().contains(IMAGE)) {

			Resource currentResource = null;
			for (Resource resource : note.getResources()) {
				if (Utils.bytesToHex(resource.getData().getBodyHash()).equals(hash.getValue())) {
					currentResource = resource;
				}
			}

			if (currentResource == null) {
				throw new MissingResourceException(note.getTitle());
			}

			List<Attribute> newAttrs = new ArrayList<Attribute>();
			for (@SuppressWarnings("unchecked")
			Iterator<Attribute> iterator = start.getAttributes(); iterator.hasNext();) {
				Attribute attr = iterator.next();
				if (HASH.equals(attr.getName().getLocalPart())) {
					Attribute src = getEventFactory().createAttribute(SRC,
							mapHashURL.get(hash.getValue()) != null ? mapHashURL.get(hash.getValue()) : "");
					newAttrs.add(src);
				} else if (!TYPE.equals(attr.getName().getLocalPart())) {
					// type is not a supported attribute for img tag.
					newAttrs.add(attr);
				}
			}
			newAttrs.add(getEventFactory().createAttribute(
					ALT,
					currentResource.getAttributes().getFileName() != null ? currentResource.getAttributes()
							.getFileName() : ""));
			return new Elements(getEventFactory().createStartElement("", "", IMG, newAttrs.iterator(),
					start.getNamespaces()), getEventFactory().createEndElement("", "", IMG));
		} else {
			return new Elements(getEventFactory().createStartElement(
					"",
					"",
					A,
					Arrays.asList(
							getEventFactory().createAttribute(HREF,
									mapHashURL.get(hash.getValue()) != null ? mapHashURL.get(hash.getValue()) : ""),
							type,
							getEventFactory().createAttribute(STYLE,
									"text-decoration: none;color: #6f6f6f;position: relative; display: block;"))
							.iterator(), start.getNamespaces()), getEventFactory().createEndElement("", "", A));
		}
	}

	/**
	 * If the {@code <en-media>} tag currently processed contains a non image file, add and additional
	 * {@code <img></img>} tag to display an icon in the {@code <a></a>} tag created by
	 * {@link DefaultMediaTagConverter#convertElement(StartElement, Note, Map)}.
	 */
	public final List<XMLEvent> insertIn(final StartElement start, final Note note, final Map<String, String> mapHashURL) {

		List<XMLEvent> result = new ArrayList<XMLEvent>();

		Attribute type = start.getAttributeByName(new QName(TYPE));
		Attribute hash = start.getAttributeByName(new QName(HASH));

		if (!type.getValue().contains(IMAGE)) {

			Resource currentResource = null;
			for (Resource resource : note.getResources()) {
				if (Utils.bytesToHex(resource.getData().getBodyHash()).equals(hash.getValue())) {
					currentResource = resource;
				}
			}

			if (currentResource == null) {
				throw new MissingResourceException(note.getTitle());
			}

			result.add(getEventFactory().createStartElement(
					"",
					"",
					IMG,
					Arrays.asList(
							getEventFactory().createAttribute(ALT, ""),
							getEventFactory().createAttribute(TITLE, currentResource.getAttributes().getFileName()),
							type,
							getEventFactory().createAttribute(STYLE, "position:absolute;border-color:transparent;"),
							getEventFactory().createAttribute(SRC,
									DATA + type.getValue() + BASE64 + Utils.getEncodedIcon(type.getValue())))
							.iterator(), null));
			result.add(getEventFactory().createStartElement(
					"",
					"",
					SPAN,
					Arrays.asList(
							getEventFactory().createAttribute(TITLE, currentResource.getAttributes().getFileName()),
							getEventFactory().createAttribute(STYLE,
									"display: block;line-height: 48px;margin-left: 56px;")).iterator(), null));

			result.add(getEventFactory().createCharacters(currentResource.getAttributes().getFileName()));

			result.add(getEventFactory().createEndElement("", "", SPAN));
			result.add(getEventFactory().createEndElement("", "", IMG));
		}
		return result;
	}

	/**
	 * This {@code Converter} does not add any tag before the {@code <img></img>} or {@code <a></a>} tag created.
	 */
	public final List<XMLEvent> insertBefore(final StartElement start, final Note note,
			final Map<String, String> mapHashURL) {
		return null;
	}

	/**
	 * This {@code Converter} does not add any tag after the {@code <img></img>} or {@code <a></a>} tag created.
	 */
	public final List<XMLEvent> insertAfter(final StartElement start, final Note note,
			final Map<String, String> mapHashURL) {
		return null;
	}

	/**
	 * This {@code Converter} does not replace any text in the {@code <img></img>} or {@code <a></a>} tag created.
	 */
	public final Characters convertCharacter(final Characters characters, final StartElement start, final Note note,
			final Map<String, String> mapHashURL) {
		return characters;
	}

}
