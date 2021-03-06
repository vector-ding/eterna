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

package self.micromagic.eterna.search;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import self.micromagic.eterna.share.EternaException;
import self.micromagic.eterna.model.AppData;
import self.micromagic.eterna.search.impl.SearchAdapterImpl;
import self.micromagic.eterna.share.EternaFactory;
import self.micromagic.eterna.share.TypeManager;
import self.micromagic.eterna.sql.QueryAdapter;
import self.micromagic.eterna.sql.ResultIterator;
import self.micromagic.eterna.sql.ResultReaderManager;
import self.micromagic.eterna.sql.ResultRow;
import self.micromagic.eterna.sql.preparer.PreparerManager;
import self.micromagic.eterna.sql.preparer.ValuePreparer;
import self.micromagic.eterna.sql.preparer.ValuePreparerCreater;
import self.micromagic.eterna.security.UserManager;
import self.micromagic.eterna.security.User;
import self.micromagic.eterna.security.EmptyPermission;
import self.micromagic.util.BooleanRef;
import self.micromagic.util.StringAppender;
import self.micromagic.util.StringTool;

/**
 * 两次查询的搜索.
 * 第一次查询, 取出所有的主键; 第二次查询, 根据前一次查询
 * 的主键取出正式的结果. <p>
 *
 * 可设置的属性名称:
 *
 * nextQueryName(必选)             第二次查询的query名称
 *
 * keyConditionIndex               上面这个query中设置key条件的索引值, 默认值为1
 *
 * keyNameList(必选)               主键名称列表, 格式为  name1,name2,... 或 name1:col1,name2,...
 *
 * assistSearchName                辅助的search名称, 用于设置其他的条件, 可使用$same表示使用本search
 *
 * needAssistCondition             是否需要辅助search的条件, 当assistSearchName为$same时默认值为false
 *                                 当assistSearchName为其它search的名称时默认值为true
 *
 * @author micromagic@sina.com
 */
public class DoubleQuerySearch extends SearchAdapterImpl
		implements SearchAdapter, SearchAdapterGenerator
{
	private String sessionNextQueryTag;
	private int nextQueryIndex;
	private int keyConditionIndex = 1;
	private int[] keyIndexs;
	private String[] colNames;
	private ValuePreparerCreater[] vpcs;
	private int assistSearchIndex = -1;
	private boolean sameSearch = false;
	private boolean needAssistCondition = false;

	/**
	 * 每个条件单元所占的字符数.
	 */
	private int conditionItemSize = 0;

	private boolean initialized = false;

	public void initialize(EternaFactory factory)
			throws EternaException
	{
		if (this.initialized)
		{
			return;
		}
		this.initialized = true;
		super.initialize(factory);
		String tmp;

		tmp = (String) this.getAttribute("nextQueryName");
		if (tmp == null)
		{
			throw new EternaException("Not found attribute [nextQueryName].");
		}
		this.sessionNextQueryTag = "q:" + tmp + ":" + factory.getFactoryContainer().getId();
		this.nextQueryIndex = factory.getQueryAdapterId(tmp);
		tmp = (String) this.getAttribute("keyConditionIndex");
		if (tmp != null)
		{
			this.keyConditionIndex = Integer.parseInt(tmp);
		}

		tmp = (String) this.getAttribute("keyNameList");
		if (tmp == null)
		{
			throw new EternaException("Not found attribute [keyNameList].");
		}
		String[] keyList = StringTool.separateString(tmp, ",", true);
		QueryAdapter keyQuery = factory.createQueryAdapter(this.getQueryName());
		ResultReaderManager keyReaders = keyQuery.getReaderManager();
		this.keyIndexs = new int[keyList.length];
		this.colNames = new String[keyList.length];
		this.vpcs = new ValuePreparerCreater[keyList.length];
		for (int i = 0; i < keyList.length; i++)
		{
			String str = keyList[i];
			int tmpI = str.indexOf(':');
			String keyN, colN;
			if (tmpI == -1)
			{
				keyN = colN = str;
			}
			else
			{
				keyN = str.substring(0, tmpI);
				colN = str.substring(tmpI + 1);
			}
			this.keyIndexs[i] = keyReaders.getIndexByName(keyN);
			this.vpcs[i] = factory.createValuePreparerCreater(
					TypeManager.getPureType(keyReaders.getReaderInList(this.keyIndexs[i] - 1).getType()));
			this.colNames[i] = colN;
			this.conditionItemSize += colN.length() + 4;
			if (i > 1)
			{
				this.conditionItemSize += 10;
			}
		}

		tmp = (String) this.getAttribute("assistSearchName");
		if (tmp != null)
		{
			if ("$same".equals(tmp))
			{
				this.sameSearch = true;
			}
			else
			{
				this.assistSearchIndex = factory.getSearchAdapterId(tmp);
				this.needAssistCondition = true;
			}
		}
		tmp = (String) this.getAttribute("needAssistCondition");
		if (tmp != null)
		{
			this.needAssistCondition = "true".equalsIgnoreCase(tmp);
		}
	}

	public Result doSearch(AppData data, Connection conn)
			throws EternaException, SQLException
	{
		if (this.needSynchronize)
		{
			synchronized (this)
			{
				return this.doSearch0(data, conn);
			}
		}
		return this.doSearch0(data, conn);
	}

	protected Result doSearch0(AppData data, Connection conn)
			throws EternaException, SQLException
	{
		Result result = super.doSearch0(data, conn, true);
		ResultIterator ritr = result.queryResult;
		QueryAdapter nextQuery;
		if (this.sameSearch || this.assistSearchIndex != -1)
		{
			SearchAdapter assistSearch = this.sameSearch ? this
					: this.getFactory().createSearchAdapter(this.assistSearchIndex);
			BooleanRef first = new BooleanRef();
			SearchManager manager = assistSearch.getSearchManager(data);
			nextQuery = getQueryAdapter(data, conn, assistSearch, first, this.sessionNextQueryTag, manager,
					this.nextQueryIndex, assistSearch.getColumnSetting(), assistSearch.getColumnSettingType());
			if (this.needAssistCondition)
			{
				dealOthers(data, conn, assistSearch.getOtherSearchs(), nextQuery, first.value);
				if (assistSearch.getConditionIndex() > 0)
				{
					if (assistSearch.isSpecialCondition())
					{
						String subConSQL = manager.getSpecialConditionPart(assistSearch, assistSearch.isNeedWrap());
						PreparerManager spm = manager.getSpecialPreparerManager(assistSearch);
						nextQuery.setSubSQL(assistSearch.getConditionIndex(), subConSQL, spm);
					}
					else
					{
						nextQuery.setSubSQL(assistSearch.getConditionIndex(),
								manager.getConditionPart(assistSearch.isNeedWrap()), manager.getPreparerManager());
					}
				}
			}
			if (assistSearch.getParameterSetting() != null)
			{
				assistSearch.getParameterSetting().setParameter(nextQuery, assistSearch, first.value, data, conn);
			}
		}
		else
		{
			nextQuery = this.getFactory().createQueryAdapter(this.nextQueryIndex);
			UserManager um = this.getFactory().getUserManager();
			if (um != null)
			{
				User user = um.getUser(data);
				if (user != null)
				{
					nextQuery.setPermission(user.getPermission());
				}
				else
				{
					nextQuery.setPermission(EmptyPermission.getInstance());
				}
			}
		}
		if (nextQuery.canOrder())
		{
			if (result.singleOrderName != null)
			{
				nextQuery.setSingleOrder(result.singleOrderName, result.singleOrderDesc ? -1 : 1);
			}
		}
		nextQuery.setTotalCount(ritr.getRealRecordCount(),
				new QueryAdapter.TotalCountExt(ritr.isHasMoreRecord(), ritr.isRealRecordCountAvailable()));
		this.setNextQueryCondition(ritr, nextQuery);
		return new Result(result, nextQuery.getName(), nextQuery.executeQuery(conn));
	}

	/**
	 * 获取执行第二次查询的query对象.
	 */
	private void setNextQueryCondition(ResultIterator keyIterator, QueryAdapter nextQuery)
			throws EternaException, SQLException
	{
		boolean multiKey = this.keyIndexs.length > 1;
		if (keyIterator.getRecordCount() == 0)
		{
			nextQuery.setSubSQL(this.keyConditionIndex, "(" + this.colNames[0] + " = null)");
		}
		else
		{
			StringAppender buf = StringTool.createStringAppender(
					this.conditionItemSize * keyIterator.getRecordCount());
			List vpList = new LinkedList();
			int vpIndex = 0;
			buf.append('(');
			while (keyIterator.hasMoreRow())
			{
				if (multiKey)
				{
					buf.append('(');
				}
				ResultRow row = keyIterator.nextRow();
				for (int i = 0; i < this.keyIndexs.length; i++)
				{
					if (i > 0)
					{
						buf.append(" AND ");
					}
					int keyIndex = this.keyIndexs[i];
					Object keyValue = row.getObject(keyIndex);
					if (keyValue == null)
					{
						buf.append(this.colNames[i]).append(" is null");
					}
					else
					{
						ValuePreparer vp = this.vpcs[i].createPreparer(keyValue);
						vp.setRelativeIndex(++vpIndex);
						vpList.add(vp);
						buf.append(this.colNames[i]).append(" = ?");
					}
				}
				if (multiKey)
				{
					buf.append(')');
				}
				if (keyIterator.hasMoreRow())
				{
					buf.append(" OR ");
				}
			}
			buf.append(')');
			if (vpList.size() > 0)
			{
				PreparerManager pm = new PreparerManager(vpList.size());
				for (Iterator iterator = vpList.iterator(); iterator.hasNext();)
				{
					pm.setValuePreparer((ValuePreparer) iterator.next());
				}
				nextQuery.setSubSQL(this.keyConditionIndex, buf.toString(), pm);
			}
			else
			{
				nextQuery.setSubSQL(this.keyConditionIndex, buf.toString());
			}
		}
	}

}