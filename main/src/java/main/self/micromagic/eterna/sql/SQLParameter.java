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

package self.micromagic.eterna.sql;

import self.micromagic.eterna.share.EternaException;
import self.micromagic.eterna.share.EternaFactory;
import self.micromagic.eterna.sql.preparer.ValuePreparer;

public interface SQLParameter
{
	/**
	 * 初始化本SQLParameter.
	 */
	void initialize(EternaFactory factory) throws EternaException;

	/**
	 * 获取这个SQLParameter的名称.
	 */
	String getName() throws EternaException;

	/**
	 * 获取对应的列名.
	 */
	String getColumnName() throws EternaException;

	/**
	 * 获取这个SQLParameter的数据类型.
	 */
	int getType() throws EternaException;

	/**
	 * 获取这个SQLParameter的纯数据类型.
	 */
	int getPureType() throws EternaException;

	/**
	 * 获取这个SQLParameter的参数索引值.
	 */
	int getIndex() throws EternaException;

	/**
	 * 获取这个SQLParameter的数据类型名称.
	 */
	String getTypeName() throws EternaException;

	/**
	 * 通过String类型的数据构成一个ValuePreparer.
	 */
	ValuePreparer createValuePreparer(String value) throws EternaException;

	/**
	 * 通过Object类型的数据构成一个ValuePreparer.
	 */
	ValuePreparer createValuePreparer(Object value) throws EternaException;

}