/*
 * $Id: LuaJavaAPI.java,v 1.4 2006/12/22 14:06:40 thiago Exp $
 * Copyright (C) 2003-2007 Kepler Project.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Softwarea.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.luajava;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.*;
import android.util.*;
import java.util.*;

/**
 * Class that contains functions accessed by lua.
 * 
 * @author Thiago Ponte
 */
public final class LuaJavaAPI
{
	static HashMap <String,Method[]>methodsMap = new HashMap<String,Method[]>();
	static HashMap <String,Method[]>methodCache = new HashMap<String,Method[]>();

	private LuaJavaAPI()
	{
	}



	/**
	 * Java implementation of the metamethod __index
	 * 
	 * @param luaState int that indicates the state used
	 * @param obj Object to be indexed
	 * @param methodName the name of the method
	 * @return number of returned objects
	 */

	public static int objectIndex(int luaState, Object obj, String searchName, int type)
	throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);
		synchronized (L)
		{
			int ret=0;
			if (type == 0)
				if (checkMethod(L, obj, searchName) != 0)
					return 2;

			if (type == 0 || type == 1 || type == 5)
				if ((ret = checkField(L, obj, searchName)) != 0)
					return ret;

			if (type == 0 || type == 4)
				if (javaGetter(L, obj, searchName) != 0)
					return 4;

			if (type == 0 || type == 3)
				if (checkClass(L, obj, searchName) != 0)
					return 3;
			/*
			 res = checkDeclaredField(L, obj, searchName);
			 if (res != 0)
			 return 1;

			 res = checkDeclaredMethod(L, obj, searchName);
			 if (res != 0)
			 return 2;

			 res = checkDeclaredClass(L, obj, searchName);
			 if (res != 0)
			 return 1;
			 */

			return 0;
		}
	}

	public static int callMethod(int luaState, Object obj, String cacheName)
	throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{
			Method[] methods=methodCache.get(cacheName);
			int top = L.getTop();
			Object[] objs = new Object[top];
			Method method = null;
			// gets method and arguments
			for (int i = 0; i < methods.length; i++)
			{

				Class[] parameters = methods[i].getParameterTypes();
				if (parameters.length != top)
					continue;

				boolean okMethod = true;

				for (int j = 0; j < parameters.length; j++)
				{
					try
					{
						objs[j] = compareTypes(L, parameters[j], j + 1);

					}
					catch (Exception e)
					{
						okMethod = false;
						break;
					}
				}

				if (okMethod)
				{
					method = methods[i];
					break;
				}

			}

			// If method is null means there isn't one receiving the given arguments
			if (method == null)
			{
				StringBuilder msgbuilder = new StringBuilder();
				for (int i=0;i < methods.length;i++)
				{
					msgbuilder.append(methods[i].toString());
					msgbuilder.append("\n");
				}
				throw new LuaException("Invalid method call. Invalid Parameters.\n" + msgbuilder.toString());
			}

			Object ret;
			try
			{
				if (!Modifier.isPublic(method.getModifiers()))
					method.setAccessible(true);

				ret = method.invoke(obj, objs);
			}
			catch (Exception e)
			{
				throw new LuaException(e);
			}

			// Void function returns null
			//Class retType = method.getReturnType();
			if (ret == null && method.getReturnType().equals(void.class))
				return 0;

			// push result
			L.pushObjectValue(ret);

			return 1;
		}
	}



	/**
	 * Java implementation of the metamethod __newindex
	 * 
	 * @param luaState int that indicates the state used
	 * @param obj Object to be indexed
	 * @param methodName the name of the method
	 * @return number of returned objects
	 */

	public static int objectNewIndex(int luaState, Object obj, String searchName)
	throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);
		synchronized (L)
		{
			int res;

			res = setFieldValue(L, obj, searchName);
			if (res != 0)
				return 1;

			res = javaSetter(L, obj, searchName);
			if (res != 0)
				return 1;

			return 0;
		}
	}


	public static int setFieldValue(LuaState L, Object obj, String fieldName) throws LuaException
	{
		synchronized (L)
		{
			Field field = null;
			Class objClass;
			boolean isClass = false;

			if (obj == null)
				return 0;

			if (obj instanceof Class)
			{
				objClass = (Class) obj;
				isClass = true;
			}
			else
			{
				objClass = obj.getClass();
			}

			try
			{
				field = objClass.getField(fieldName);
			}
			catch (NoSuchFieldException e)
			{
				try
				{
					field = objClass.getDeclaredField(fieldName);
				}
				catch (Exception e2)
				{
					return 0;
				}
			}

			if (field == null)
				return 0;
			if (isClass && !Modifier.isStatic(field.getModifiers()))
				return 0;

			try
			{
				if (!Modifier.isPublic(field.getModifiers()))
					field.setAccessible(true);
				Class type = field.getType();
				field.set(obj, compareTypes(L, type, 3));
			}
			catch (Exception e)
			{
				throw new LuaException(e);
			}

			return 1;
		}
	}

	/**
	 * Java implementation of the metamethod __index
	 * 
	 * @param luaState int that indicates the state used
	 * @param obj Object to be indexed
	 * @param methodName the name of the method
	 * @return number of returned objects
	 */
	public static int setArrayValue(int luaState, Object obj, int index) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{
			Class type = obj.getClass().getComponentType();
			if (type == null)
				throw new LuaException(obj.toString() + " is not a array");
			try
			{
				Object value = compareTypes(L, type, 3);
				Array.set(obj, index, value);
			}
			catch (Exception e)
			{
				throw new LuaException("can not set array value: " + e.getMessage());
			}

			return 0;
		}
	}

	public static int getArrayValue(int luaState, Object obj, int index) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{

			try
			{
				Object ret=Array.get(obj, index);
				L.pushObjectValue(ret);
				return 1;
			}
			catch (Exception e)
			{
				throw new LuaException("can not get array value: " + e.getMessage());
			}

		}
	}
	public static int asTable(int luaState, Object obj) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{

			try
			{
				L.newTable();
				if (obj.getClass().isArray())
				{
					int n=Array.getLength(obj);
					for (int i=0;i <= n - 1 ;i++)
					{
						L.pushObjectValue(Array.get(obj, i));
						L.rawSetI(-2, i);
					}
				}
				else if (obj instanceof ArrayList)
				{
					ArrayList al=(ArrayList)obj;
					int n=al.size();
					for (int i=0;i <= n - 1 ;i++)
					{
						L.pushObjectValue(al.get(i));
						L.rawSetI(-2, i);
					}
				}
				else if (obj instanceof HashMap)
				{
					HashMap hm=(HashMap)obj;
					//Set<Map.Entry> sets =hm.entrySet(); 
					for (Map.Entry entry : hm.entrySet())
					{ 
						L.pushObjectValue(entry.getKey());
						L.pushObjectValue(entry.getValue());
						L.setTable(-3);
					}
					/*
					 HashMap hm=(HashMap)obj;
					 Iterator iter = hm.entrySet().iterator();
					 while (iter.hasNext())
					 {
					 Map.Entry entry = (Map.Entry) iter.next();
					 L.pushObjectValue(entry.getKey());
					 L.pushObjectValue(entry.getValue());
					 L.setTable(-3);
					 }
					 */

				}
				L.pushValue(-1);
				return 1;
			}
			catch (Exception e)
			{
				throw new LuaException("can not get array value: " + e.getMessage());
			}

		}
	}

	public static int newArray(int luaState, Class clazz, int size) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);
		synchronized (L)
		{
			try
			{
				Object obj = Array.newInstance(clazz, size);
				L.pushJavaObject(obj);
			}
			catch (Exception e)
			{
				throw new LuaException("can not create a array: " + e.getMessage());
			}
			return 1;
		}
	}


	public static Class javaBindClass(String className) throws LuaException
	{
		Class clazz;
		try
		{
			clazz = Class.forName(className);
		}
		catch (Exception e)
		{
			if (className.equals("boolean"))
				clazz = boolean.class;
			else if (className.equals("byte"))
				clazz = byte.class;
			else if (className.equals("char"))
				clazz = char.class;
			else if (className.equals("short"))
				clazz = short.class;
			else if (className.equals("int"))
				clazz = int.class;
			else if (className.equals("long"))
				clazz = long.class;
			else if (className.equals("float"))
				clazz = float.class;
			else if (className.equals("double"))
				clazz = double.class;
			else 
				throw new LuaException("Class not found: " + className);
		}
		return clazz;
	}



	/**
	 * Pushes a new instance of a java Object of the type className
	 * 
	 * @param luaState int that represents the state to be used
	 * @param className name of the class
	 * @return number of returned objects
	 * @throws LuaException
	 */
	public static int javaNewInstance(int luaState, String className) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{
			Class clazz;
			clazz = javaBindClass(className);
			if (clazz.isPrimitive())
				return toPrimitive(L, clazz, -1);
			else
				return getObjInstance(L, clazz);
		}
	}

	/**
	 * javaNew returns a new instance of a given clazz
	 * 
	 * @param luaState int that represents the state to be used
	 * @param clazz class to be instanciated
	 * @return number of returned objects
	 * @throws LuaException
	 */
	public static int javaNew(int luaState, Class clazz) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{
			if (clazz.isPrimitive())
			{
				return toPrimitive(L, clazz, -1);
			}
			else
			{			
				return getObjInstance(L, clazz);
			}
		}
	}

	public static int javaCreate(int luaState, Class clazz) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{
			if (clazz.isInterface())
			{
				String implem = clazz.getName();
				return createProxyObject(L, implem);
			}
			else
			{
				return createArray(L, clazz);
			}
		}

	}

	/**
	 * Function that creates an object proxy and pushes it into the stack
	 * 
	 * @param luaState int that represents the state to be used
	 * @param implem interfaces implemented separated by comma (<code>,</code>)
	 * @return number of returned objects
	 * @throws LuaException
	 */
	public static int createProxy(int luaState, String implem)
	throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);
		synchronized (L)
		{
			return createProxyObject(L, implem);
		}
	}

	public static int createArray(int luaState, String className)
	throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);
		synchronized (L)
		{
			Class type=javaBindClass(className);
			return createArray(L, type);
		}
	}

	/**
	 * Calls the static method <code>methodName</code> in class <code>className</code>
	 * that receives a LuaState as first parameter.
	 * @param luaState int that represents the state to be used
	 * @param className name of the class that has the open library method
	 * @param methodName method to open library
	 * @return number of returned objects
	 * @throws LuaException
	 */
	public static int javaLoadLib(int luaState, String className, String methodName)
  	throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{
			Class clazz;
			try
			{
				clazz = Class.forName(className);
			}
			catch (ClassNotFoundException e)
			{
				throw new LuaException(e);
			}

			try
			{
				Method mt = clazz.getMethod(methodName, new Class[] {LuaState.class});
				Object obj = mt.invoke(null, new Object[] {L});

				if (obj != null && obj instanceof Integer)
				{
					return ((Integer) obj).intValue();
				}
				else
					return 0;
			}
			catch (Exception e)
			{
				throw new LuaException("Error on calling method. Library could not be loaded. " + e.getMessage());
			}
		}
	}

	public static int javaToString(int luaState, Object obj) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{
			if (obj == null)
				L.pushString("null");
			else
				L.pushString(obj.toString());
			return 1;
		}
	}

	public static int javaArrayLength(int luaState, Object obj) throws LuaException
	{
		LuaState L = LuaStateFactory.getExistingState(luaState);

		synchronized (L)
		{
			int ret;
			try
			{
				ret = Array.getLength(obj);
			}
			catch (Exception e)
			{
				throw new LuaException(e);
			}

			L.pushNumber(ret);

			return 1;
		}
	}


	private static int getObjInstance(LuaState L, Class clazz) throws LuaException
	{
		synchronized (L)
		{
			int top = L.getTop();
			Object[] objs = new Object[top - 1];

			Constructor[] constructors = clazz.getConstructors();
			Constructor constructor = null;

			// gets method and arguments
			for (int i = 0; i < constructors.length; i++)
			{
				Class[] parameters = constructors[i].getParameterTypes();
				if (parameters.length != top - 1)
					continue;

				boolean okConstruc = true;

				for (int j = 0; j < parameters.length; j++)
				{
					try
					{
						objs[j] = compareTypes(L, parameters[j], j + 2);
					}
					catch (Exception e)
					{
						okConstruc = false;
						break;
					}
				}

				if (okConstruc)
				{
					constructor = constructors[i];
					break;
				}

			}

			// If method is null means there isn't one receiving the given arguments
			if (constructor == null)
			{
				StringBuilder msgbuilder = new StringBuilder();
				for (int i=0;i < constructors.length;i++)
				{
					msgbuilder.append(constructors[i].toString());
					msgbuilder.append("\n");
				}
				throw new LuaException("Invalid constructor method call. Invalid Parameters.\n" + msgbuilder.toString());
			}

			Object ret;
			try
			{
				ret = constructor.newInstance(objs);
			}
			catch (Exception e)
			{
				throw new LuaException(e);
			}

			if (ret == null)
			{
				throw new LuaException("Couldn't instantiate java Object");
			}
			L.pushJavaObject(ret);
			return 1;
		}
	}

	/**
	 * Checks if there is a field on the obj with the given name
	 * 
	 * @param luaState int that represents the state to be used
	 * @param obj object to be inspected
	 * @param fieldName name of the field to be inpected
	 * @return number of returned objects
	 */
	public static int checkField(LuaState L, Object obj, String fieldName)
  	throws LuaException
	{
		synchronized (L)
		{
			Field field = null;
			Class objClass;
			boolean isClass=false;

			if (obj instanceof Class)
			{
				objClass = (Class) obj;
				isClass = true;
			}
			else
			{
				objClass = obj.getClass();
			}

			try
			{
				field = objClass.getField(fieldName);
			}
			catch (NoSuchFieldException e)
			{
				return 0;
			}

			if (field == null)
				return 0;

			if (isClass && !Modifier.isStatic(field.getModifiers()))
				return 0;

			Object ret = null;
			try
			{
				if (!Modifier.isPublic(field.getModifiers()))
					field.setAccessible(true);
				ret = field.get(obj);
			}
			catch (Exception e)
			{
				throw new LuaException(e);
			}

			L.pushObjectValue(ret);
			if (Modifier.isFinal(field.getModifiers()))
				return 5;
			else
				return 1;
		}
	}
	public static int checkDeclaredField(LuaState L, Object obj, String fieldName)
  	throws LuaException
	{
		synchronized (L)
		{
			Field field = null;
			Class objClass;
			boolean isClass=false;

			if (obj instanceof Class)
			{
				objClass = (Class) obj;
				isClass = true;
			}
			else
			{
				objClass = obj.getClass();
			}

			try
			{
				field = objClass.getDeclaredField(fieldName);
			}
			catch (NoSuchFieldException e)
			{
				return 0;
			}

			if (field == null)
				return 0;

			if (isClass && !Modifier.isStatic(field.getModifiers()))
				return 0;

			Object ret = null;
			try
			{
				field.setAccessible(true);
				ret = field.get(obj);
			}
			catch (Exception e)
			{
				throw new LuaException(e);
			}

			L.pushObjectValue(ret);
			return 1;
		}
	}

	/**
	 * Checks to see if there is a method with the given name.
	 * 
	 * @param luaState int that represents the state to be used
	 * @param obj object to be inspected
	 * @param methodName name of the field to be inpected
	 * @return number of returned objects
	 */
	public static int checkMethod(LuaState L, Object obj, String methodName) throws LuaException
	{
		synchronized (L)
		{
			Class clazz;
			boolean isClass = false;
			if (obj instanceof Class)
			{
				clazz = (Class) obj;
				isClass = true;
			}
			else
			{
				clazz = obj.getClass();
			}

			String className=clazz.getName();
			String cacheName=L.toString(-1);//String.format("%c %s %s",c,className,methodName);
			Method[]  mlist = methodCache.get(cacheName);
			if (mlist == null)
			{
				Method[] methods = methodsMap.get(className);
				if (methods == null)
				{
					methods = clazz.getMethods();
					methodsMap.put(className, methods);
				}
				ArrayList<Method> list = new ArrayList<Method>();

				for (int i = 0; i < methods.length; i++)
				{
					if (methods[i].getName().equals(methodName))
					{
						if (isClass && !Modifier.isStatic(methods[i].getModifiers()))
							continue;
						list.add(methods[i]);
					}
				}

				if (list.isEmpty() && isClass)
				{
					methods = clazz.getClass().getMethods();
					for (int i = 0; i < methods.length; i++)
					{
						if (methods[i].getName().equals(methodName))
							list.add(methods[i]);
					}

				}

				mlist = new Method[list.size()];
				list.toArray(mlist);
				methodCache.put(cacheName, mlist);
			}
			if (mlist.length == 0)
				return 0;
			//L.pushString(methodName);
			//L.pushLightJavaObject(mlist);
			return 2;
		}
	}
	public static int checkDeclaredMethod(LuaState L, Object obj, String methodName) throws LuaException
	{
		synchronized (L)
		{
			Class clazz;
			boolean isClass = false;
			if (obj instanceof Class)
			{
				clazz = (Class) obj;
				isClass = true;
			}
			else
			{
				clazz = obj.getClass();
			}

			Method[] methods = clazz.getDeclaredMethods();
			ArrayList<Method> list = new ArrayList<Method>();

			for (int i = 0; i < methods.length; i++)
			{
				if (methods[i].getName().equals(methodName))
				{
					if (isClass && !Modifier.isStatic(methods[i].getModifiers()))
						continue;
					list.add(methods[i]);
				}
			}

			if (list.isEmpty() && isClass)
			{
				methods = clazz.getClass().getMethods();
				for (int i = 0; i < methods.length; i++)
				{
					if (methods[i].getName().equals(methodName))
						list.add(methods[i]);
				}

			}

			if (list.isEmpty())
				return 0;

			Method[] mlist = new Method[list.size()];
			list.toArray(mlist);
			L.pushString(methodName);
			L.pushJavaObject(mlist);
			return 2;
		}
	}

	/**
	 * Checks to see if there is a class with the given name.
	 * 
	 * @param luaState int that represents the state to be used
	 * @param obj object to be inspected
	 * @param className name of the field to be inpected
	 * @return number of returned objects
	 */
	public static int checkClass(LuaState L, Object obj, String className) throws LuaException
	{
		synchronized (L)
		{
			Class clazz;

			if (obj instanceof Class)
			{
				clazz = (Class) obj;
			}
			else
			{
				return 0;
			}

			Class[] clazzes = clazz.getClasses();

			for (int i = 0; i < clazzes.length; i++)
			{
				if (clazzes[i].getSimpleName().equals(className))
				{
					L.getMetaTable(1);
					L.pushJavaObject(clazzes[i]);
					L.setField(-2, className);
					L.getField(-1, className);
					return 1;
				}
			}
			return 0;
		}
	}

	public static int checkDeclaredClass(LuaState L, Object obj, String className) throws LuaException
	{
		synchronized (L)
		{
			Class clazz;

			if (obj instanceof Class)
			{
				clazz = (Class) obj;
			}
			else
			{
				return 0;
			}

			Class[] clazzes = clazz.getDeclaredClasses();

			for (int i = 0; i < clazzes.length; i++)
			{
				if (clazzes[i].getSimpleName().equals(className))
				{
					L.getMetaTable(1);
					L.pushJavaObject(clazzes[i]);
					L.setField(-2, className);
					L.getField(-1, className);
					return 1;
				}
			}
			return 0;
		}
	}

	public static int javaGetter(LuaState L, Object obj, String methodName) throws LuaException
	{
		synchronized (L)
		{
			Class clazz;

			Method method=null;
			boolean isClass = false;	
			if (obj instanceof Class)
			{
				clazz = (Class) obj;
				isClass = true;
			}
			else
			{
				clazz = obj.getClass();
			}

			try
			{
				method = clazz.getMethod("get" + methodName);
			}
			catch (NoSuchMethodException e)
			{
				return 0;
			}

			if (isClass && !Modifier.isStatic(method.getModifiers()))
				return 0;

			Object ret;
			try
			{
				ret = method.invoke(obj);
			}
			catch (Exception e)
			{
				return 0;
			}

			L.pushObjectValue(ret);
			return 1;
		}
	}

	public static int javaSetter(LuaState L, Object obj, String methodName) throws LuaException
	{
		synchronized (L)
		{
			Class clazz;
			Object value = null;

			Method method=null;
			boolean isClass = false;	
			if (obj instanceof Class)
			{
				clazz = (Class) obj;
				isClass = true;
			}
			else
			{
				clazz = obj.getClass();
			}

			if (methodName.equals("onClick"))
			{
				Object listener;
				try
				{
					Method method2 = clazz.getMethod("setOnClickListener", new Class[]{android.view.View.OnClickListener.class});
					if (L.type(-1) == LuaState.LUA_TFUNCTION)
					{
						L.newTable();
						L.pushValue(-2);
						L.setField(-2, "onClick");
						listener = L.getLuaObject(-1).createProxy("android.view.View$OnClickListener");
						try
						{
							method2.invoke(obj, new Object[]{listener});
							return 1;
						}
						catch (InvocationTargetException e)
						{}
						catch (IllegalAccessException e)
						{}
						catch (IllegalArgumentException e)
						{}
					}
				}
				catch (NoSuchMethodException e)
				{}
				catch (LuaException e)
				{}
				catch (ClassNotFoundException e)
				{}	
			}

			try
			{
				switch (L.type(3))
				{
					case LuaState.LUA_TBOOLEAN:
						method = clazz.getMethod("set" + methodName, boolean.class);
						value = L.toBoolean(3);
						break;
					case LuaState.LUA_TNUMBER:
						try
						{
							method = clazz.getMethod("set" + methodName, int.class);
							value = (int)L.toInteger(3);
						}
						catch (NoSuchMethodException e)
						{
							method = clazz.getMethod("set" + methodName, float.class);
							value = (float)L.toNumber(3);
						}
						break;
					case LuaState.LUA_TSTRING:
						try
						{
							method = clazz.getMethod("set" + methodName, CharSequence.class);
						}
						catch (NoSuchMethodException e)
						{
							method = clazz.getMethod("set" + methodName, String.class);
						}
						value = L.toString(3);
						break;
					default:
						value = L.toJavaObject(3);
						Class type=value.getClass();
						while (true)
						{
							try
							{
								method = clazz.getMethod("set" + methodName, type);
								break;
							}
							catch (NoSuchMethodException e)
							{
								type = type.getSuperclass();
								if (type == null)
									return 0;
							}
						}
				}
			}
			catch (NoSuchMethodException e)
			{
				return 0;
			}

			if (isClass && !Modifier.isStatic(method.getModifiers()))
				return 0;

			try
			{
				method.invoke(obj, value);
			}
			catch (Exception e)
			{
				return 0;
			}

			//L.pushObjectValue(ret);
			return 1;
		}
	}

	public static int createProxyObject(LuaState L, String implem)
	throws LuaException
	{
		synchronized (L)
		{
			try
			{
				LuaObject luaObj = L.getLuaObject(2);
				Object proxy = luaObj.createProxy(implem);
				L.pushJavaObject(proxy);
			}
			catch (Exception e)
			{
				throw new LuaException(e);
			}

			return 1;
		}
	}

	private static int createArray(LuaState L, Class type) throws LuaException
	{
		synchronized (L)
		{
			try
			{
				int n = L.objLen(-1);
				Object array = Array.newInstance(type, n);
				/*			if (n == 0)
				 {
				 L.pushJavaObject(array.getClass());
				 return 1;
				 }*/
				if (type == String.class)
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, L.toString(-1));
						L.pop(1);
					}
				}  
				else if (type == double.class)
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, L.toNumber(-1));
						L.pop(1);
					}
				}  
				else if (type == float.class)
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, (float)L.toNumber(-1));
						L.pop(1);
					}
				}  
				else if (type == long.class)
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, L. toInteger(-1));
						L.pop(1);
					}
				}  
				else if (type == int.class)
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, (int)L.toInteger(-1));
						L.pop(1);
					}
				}
				else if (type == short.class)
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, (short)L.toInteger(-1));
						L.pop(1);
					}
				}  
				else if (type == char.class)
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, (char)L.toInteger(-1));
						L.pop(1);
					}
				}  
				else if (type == byte.class)
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, (byte)L.toInteger(-1));
						L.pop(1);
					}
				}  
				else
				{ 
					for (int i = 1;i <= n;i++)
					{
						L.pushNumber(i);
						L.getTable(-2);
						Array.set(array, i - 1, compareTypes(L, type, -1));
						L.pop(1);
					}
				}  
				L.pushJavaObject(array);
				return 1;
			}
			catch (Exception e)
			{
				throw new LuaException(e);
			}
		}
	}

	private static Object compareTypes(LuaState L, Class parameter, int idx)
	throws LuaException
	{
		boolean okType = true;
		Object obj = null;
		int type=L.type(idx);
		switch (type)
		{
			case 1: //boolean
				{
					if (parameter.isPrimitive())
					{
						if (parameter != Boolean.TYPE)
						{
							okType = false;
						}
					}
					else if (!parameter.isAssignableFrom(Boolean.class))
					{
						okType = false;
					}
					obj = L.toBoolean(idx);
				}
				break;
			case 4: //string
				{
					if (!parameter.isAssignableFrom(String.class))
					{
						okType = false;
					}
					else
					{
						obj = L.toString(idx);
					}
				}
				break;
			case 6: //function
				{
					if (!parameter.isAssignableFrom(LuaObject.class))
					{
						okType = false;
					}
					else
					{
						obj = L.getLuaObject(idx);
					}
				}
				break;
			case 5: //table
				{
					if (!parameter.isAssignableFrom(LuaObject.class))
					{
						okType = false;
					}
					else
					{
						obj = L.getLuaObject(idx);
					}
				}
				break;
			case 3: //number
				{
					Double db = new Double(L.toNumber(idx));

					obj = LuaState.convertLuaNumber(db, parameter);
					if (obj == null)
					{
						okType = false;
					}
				}
				break;
			case 7: //userdata
				{
					if (L.isObject(idx))
					{
						Object userObj = L.getObjectFromUserdata(idx);
						if (!parameter.isAssignableFrom(userObj.getClass()))
						{
							okType = false;
						}
						else
						{
							obj = userObj;
						}
					}
					else
					{
						if (!parameter.isAssignableFrom(LuaObject.class))
						{
							okType = false;
						}
						else
						{
							obj = L.getLuaObject(idx);
						}
					}
				}
				break;
			case 0: //nil
				{
					obj = null;
				}
				break;
			default: //other
				{
					throw new LuaException("Invalid Parameters.");
				}
		}
		if (!okType)
		{
			throw new LuaException("Invalid Parameter.");
		}

		return obj;
	}


	private static int toPrimitive(LuaState L, Class type, int idx) throws LuaException
	{
		if (!L.isNumber(idx))
		{
			throw new LuaException(L.toString(idx) + " is not number");
		}
		Object obj=null;
		if (type == double.class)
		{ 
			obj = L.toNumber(idx);
		}  
		else if (type == float.class)
		{ 
			obj = (float)L.toNumber(idx);
		}  
		else if (type == long.class)
		{ 
			obj = L.toInteger(idx);
		}  
		else if (type == int.class)
		{ 
			obj = (int)L.toInteger(idx);
		}
		else if (type == short.class)
		{ 
			obj = (short)L.toInteger(idx);
		}  
		else if (type == char.class)
		{ 
			obj = (char)L.toInteger(idx);
		}  
		else if (type == byte.class)
		{ 
			obj =  (byte)L.toInteger(idx);
		}  
		else if (type == boolean.class)
		{
			obj = L.toBoolean(idx);
		}
		L.pushJavaObject(obj);
		return 1;
	}	

}
