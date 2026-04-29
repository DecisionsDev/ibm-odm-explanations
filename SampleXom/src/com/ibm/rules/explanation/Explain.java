/*
 * Copyright IBM Corp. 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.rules.explanation;

import java.text.MessageFormat;

import com.ibm.rules.samples.Customer;

public class Explain {
	
	public static String collectUsefulValues() {
		return null; // Do nothing by default
	}
	
	public static String collectConditions() {
		return null; // Do nothing by default
	}
	
	public static String formatMessage(String pattern, Object[] arguments) {
		return MessageFormat.format(pattern, arguments);
	}
	
	public static String formatValue(Object value) {
		String pattern = "{0}";
		if (value instanceof Number) {
			pattern = "{0,number,#.##}";
		}
		return MessageFormat.format(pattern, value);
	}
	
	public static String formatObject(Object composite) {
		if (composite instanceof Customer customer) {
			return MessageFormat.format("{0} {1}, born in {2}", customer.getFirstName(), customer.getLastName(), customer.getBirthDate());
		}
		return composite.toString();
	}
	
	public static String formatValues(Object[] items) {
		StringBuilder buf = new StringBuilder();
		buf.append("{\n");
		for (Object item: items) {
			buf.append("\t").append(formatValue(item)).append("\n");
		}
		buf.append("}");
		return buf.toString();
	}
	
	public static String formatObjects(Object[] items) {
		StringBuilder buf = new StringBuilder();
		buf.append("{\n");
		for (Object item: items) {
			buf.append("\t").append(formatObject(item)).append("\n");
		}
		buf.append("}");
		return buf.toString();
	}

}
