/*
 * Copyright 2009-2015 xinjunli (micromagic@sina.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package self.micromagic.eterna.digester2;

import java.util.Map;

import org.dom4j.Element;

import self.micromagic.eterna.digester.BodyText;
import self.micromagic.util.IntegerRef;
import self.micromagic.util.StringTool;
import self.micromagic.util.Utility;

/**
 * 获取属性或body中的值.
 */
public class BodyAttrGetter
		implements AttrGetter
{
	static final String BODY_FLAG = "$body";

	/**
	 * 解析配置信息, 生成BodyAttrGetter.
	 * 格式: $body(attr=attrTag,body=bodyTag,r=resolve,t=trimLines,n=noLine)
	 */
	public static BodyAttrGetter parseConfig(String config, IntegerRef position)
	{
      int beginPos = config.indexOf(BODY_FLAG, position.value);
		if (beginPos == -1 || config.substring(position.value, beginPos).trim().length() > 0)
		{
			// 不是以$body起始的不能解析为BodyAttrGetter
			throw new ParseException("Error config [" + config + "] for BodyAttr.");
		}
		position.value = beginPos + BODY_FLAG.length();
		Map params = ParseRule.parseParam(config, position);
		BodyAttrGetter result;
		if (params != null)
		{
			String bodyTag = null, attrTag = null;
			bodyTag = (String) params.get("body");
			attrTag = (String) params.get("attr");
			result = new BodyAttrGetter(bodyTag, attrTag);
			String bStr;
			if ((bStr = (String) params.get("t")) != null)
			{
				result.setTrimLines((String) params.get("tFlag"), ParseRule.booleanConverter.convertToBoolean(bStr));
			}
			if ((bStr = (String) params.get("n")) != null)
			{
				result.setNoLine((String) params.get("nFlag"), ParseRule.booleanConverter.convertToBoolean(bStr));
			}
			if ((bStr = (String) params.get("r")) != null)
			{
				result.setResolve((String) params.get("rFlag"), ParseRule.booleanConverter.convertToBoolean(bStr));
			}
			if ((bStr = (String) params.get("i")) != null)
			{
				result.setIntern(ParseRule.booleanConverter.convertToBoolean(bStr));
			}
			if ((bStr = (String) params.get("m")) != null)
			{
				result.setMustExists(ParseRule.booleanConverter.convertToBoolean(bStr));
			}
		}
		else
		{
			result = new BodyAttrGetter(null, null);
		}
		return result;
	}

	/**
	 * @param bodyTag  body文本所在的标签名, 设为null表示当前body的文本
	 * @param attrTag  当body不存在时获取的属性值, 设为null表示没有属性值
	 */
	public BodyAttrGetter(String bodyTag, String attrTag)
	{
		this.bodyTag = bodyTag;
		this.attrTag = attrTag;
	}
	private final String bodyTag;
	private final String attrTag;

	public void setIntern(boolean b)
	{
		this.intern = b;
	}
	private boolean intern = false;

	public void setMustExists(boolean b)
	{
		this.mustExists = b;
	}
	private boolean mustExists = true;

	public String get(Element el)
	{
		String bText = null;
		Element attrEl = el;
		if (this.bodyTag == null)
		{
			bText = el.getText();
		}
		else
		{
			Element bE = el.element(this.bodyTag);
			if (bE != null)
			{
				bText = bE.getText();
				attrEl = bE;
			}
		}
		String aText = null;
		if (this.attrTag != null)
		{
			aText = el.attributeValue(this.attrTag);
		}
		if (StringTool.isEmpty(bText) && aText == null)
		{
			if (this.mustExists)
			{
				String aPos;
				if (this.attrTag != null)
				{
					aPos = "in arrtibute [" + this.attrTag + "] or "
							+ (this.bodyTag != null ? "[" + this.bodyTag + "]'s " : "") + "body";
				}
				else
				{
					aPos = "in " + (this.bodyTag != null ? "[" + this.bodyTag + "]'s " : "") + "body";
				}
				throw new ParseException("Not found value " + aPos + " for tag [" + el.getName() + "].");
			}
			return null;
		}
		if (aText != null)
		{
			if (!StringTool.isEmpty(bText))
			{
				throw new ParseException("Both arrtibute [" + this.attrTag + "] and "
						+ (this.bodyTag != null ? "[" + this.bodyTag + "]'s " : "")
						+ "body are both setted value.");
			}
			bText = aText;
		}

		String strValue = attrEl.attributeValue(this.trimLinesAttr);
		if (strValue != null)
		{
			this.trimLines = "true".equalsIgnoreCase(strValue);
		}
		strValue = attrEl.attributeValue(this.noLineAttr);
		if (strValue != null)
		{
			this.noLine = "true".equalsIgnoreCase(strValue);
		}
		strValue = attrEl.attributeValue(this.resolveAttr);
		if (strValue != null)
		{
			this.resolve = "true".equalsIgnoreCase(strValue);
		}

		if (this.trimLines)
		{
			BodyText bodyText = new BodyText();
			bodyText.append(bText.toCharArray(), 0, bText.length());
			bText = bodyText.trimEveryLineSpace(this.noLine);
		}
		if (this.resolve)
		{
			bText = Utility.resolveDynamicPropnames(bText);
		}
		if (this.intern)
		{
			bText = StringTool.intern(bText, true);
		}
		return bText;
	}

	public void setTrimLines(String attr, boolean defalutValue)
	{
		if (attr != null)
		{
			this.trimLinesAttr = attr;
		}
		this.trimLines = defalutValue;
	}
	private boolean trimLines = true;
	private String trimLinesAttr = "trimLine";

	public void setNoLine(String attr, boolean defalutValue)
	{
		if (attr != null)
		{
			this.noLineAttr = attr;
		}
		this.noLine = defalutValue;
	}
	private boolean noLine = false;
	private String noLineAttr = "noLine";

	public void setResolve(String attr, boolean defalutValue)
	{
		if (attr != null)
		{
			this.resolveAttr = attr;
		}
		this.resolve = defalutValue;
	}
	private String resolveAttr = "resolve";
	private boolean resolve = false;

}