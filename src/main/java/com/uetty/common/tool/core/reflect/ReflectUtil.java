package com.uetty.common.tool.core.reflect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * 反射工具类
 * @author vince
 */
@SuppressWarnings("unused")
public class ReflectUtil {
	
	private static final Logger logger = LoggerFactory.getLogger(ReflectUtil.class);

	private static final WeakHashMap<Class<?>, Field[]> FIELDS_CACHE = new WeakHashMap<>();

	private static String getterName(String fieldName) {
		return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
	}
	
	private static String setterName(String fieldName) {
		return "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
	}
	
	public static Object getFieldValue(Object obj, String fieldName) {
		Class<?> clz = obj.getClass();
		try {
			String getterName = getterName(fieldName);
			return invokeMethod(obj, getterName);
		} catch (Exception ignore) {}
		try {
			Field field = clz.getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(obj);
		} catch (Exception ignore) {}
		try {
			Field field = clz.getField(fieldName);
			return field.get(obj);
		} catch (Exception ignore) {}
		throw new RuntimeException("field[" + fieldName + "] not found in " + obj);
	}
	
	public static void setFieldValue(Object obj, String fieldName, Object value) {
		Class<?> clz = obj.getClass();

		try {
			String setterName = setterName(fieldName);
			invokeMethod(obj, setterName, value);
			return;
		} catch (Exception ignore) {}
		
		try {
			Field field = clz.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(obj, value);
			return;
		} catch (Exception ignore) {}
		try {
			Field field = clz.getField(fieldName);
			field.set(obj, value);
			return;
		} catch (Exception ignore) {}
		throw new RuntimeException("field[" + fieldName + "] not found in " + obj);
	}
	
	public static List<String> getFieldNames(Object obj) {
		Class<?> clz = obj.getClass();
		Field[] fields = clz.getDeclaredFields();
		List<String> list = new ArrayList<>();
		for (Field field : fields) {
			String name = field.getName();
			list.add(name);
		}
		return list;
	}

	public static Field[] getFields(Object obj) {
		Class<?> clz = obj.getClass();
		Field[] fields = FIELDS_CACHE.get(clz);
		if (fields != null) {
			return fields;
		}
		synchronized (FIELDS_CACHE) {
			return FIELDS_CACHE.computeIfAbsent(clz, key -> getFieldsDirectly(key, true));
		}
	}

	public static Field[] getFieldsDirectly(Class<?> beanClass, boolean withSuperClassFields) throws SecurityException {
		List<Field> allFields = new ArrayList<>();
		Class<?> searchType = beanClass;
		Field[] declaredFields;
		while (searchType != null) {
			declaredFields = searchType.getDeclaredFields();
			allFields.addAll(Arrays.asList(declaredFields));
			searchType = withSuperClassFields ? searchType.getSuperclass() : null;
		}

		return allFields.toArray(new Field[0]);
	}
	
	public static Class<?> getFieldClass(Object obj, String fieldName) {
		Class<?> clz = obj.getClass();
		try {
			Field field = clz.getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.getType();
		} catch (Exception ignore) {}
		try {
			Field field = clz.getField(fieldName);
			return field.getType();
		} catch (Exception ignore) {}
		throw new RuntimeException("field[" + fieldName + "] not found in " + obj);
	}
	
	public static Object getInstance(Class<?> clz, Object... params) {
		Constructor<?>[] constructors = clz.getConstructors();
		for (Constructor<?> constructor : constructors) {
			try {
				Class<?>[] parameterTypes = constructor.getParameterTypes();
				if (parameterTypes.length != params.length) {
					continue;
				}
				constructor.setAccessible(true);
				return constructor.newInstance(params);
			} catch (Exception ignore) {}
		}
		if (params.length == 0) {
			try {
				return clz.newInstance();
			} catch (Exception ignore) {}
		}
		
		StringBuilder errorMsg = new StringBuilder("constructor(");
		for (int i = 0; i < params.length; i++) {
			if (i != 0) errorMsg.append(", ");
			errorMsg.append(params[i] == null ? "null" : params[i].getClass().getName());
		}
		errorMsg.append(") not found in class[").append(clz.getCanonicalName()).append("]");
		throw new RuntimeException(errorMsg.toString());
	}
	
	public static Object invokeMethod(Object obj, String methodName, Object... params) {
		Class<?> clz = obj.getClass();
		if (params.length == 0) {
			try {
				Method method = clz.getMethod(methodName);
				return method.invoke(obj);
			} catch (Exception ignore) {}
		} else {
			// 参数类型绝对匹配的方法查找
			boolean noNull = true;
			Class<?>[] pclzs = new Class<?>[params.length];
			for (int i = 0; i < params.length; i++) {
				Object p = params[i];
				if (p == null) {
					noNull = false;
					break;
				}
				pclzs[i] = p.getClass();
			}
			if (noNull) {
				try {
					Method method = clz.getMethod(methodName, pclzs); // 参数类型绝对匹配的方法
					return method.invoke(obj, params);
				} catch (Exception ignore) {}
			}
			
			// 不追求绝对匹配，只要能调用即可
			Method[] methods = clz.getMethods();
			for (Method m : methods) {
				try {
					if (!m.getName().equals(methodName)) continue;
					Class<?>[] parameterTypes = m.getParameterTypes();
					if (parameterTypes.length != params.length) continue;
					return m.invoke(obj, params);
				} catch (Exception ignore) {}
			}
		}
		
		StringBuilder errorMsg = new StringBuilder("method(");
		for (int i = 0; i < params.length; i++) {
			if (i != 0) errorMsg.append(", ");
			errorMsg.append(params[i] == null ? "null" : params[i].getClass().getName());
		}
		errorMsg.append(") not found in class[").append(clz.getCanonicalName()).append("]");
		throw new RuntimeException(errorMsg.toString());
	}
	
	public static Object invokeMethod(Class<?> clz, String methodName, Object... params) {
		if (params.length == 0) {
			try {
				Method method = clz.getMethod(methodName);
				return method.invoke(null);
			} catch (Exception ignore) {}
		} else {
			// 参数类型绝对匹配的方法查找
			boolean noNull = true;
			Class<?>[] pclzs = new Class<?>[params.length];
			for (int i = 0; i < params.length; i++) {
				Object p = params[i];
				if (p == null) {
					noNull = false;
					break;
				}
				pclzs[i] = p.getClass();
			}
			if (noNull) {
				try {
					Method method = clz.getMethod(methodName, pclzs); // 参数类型绝对匹配的方法
					return method.invoke(null, params);
				} catch (Exception ignore) {}
			}
			
			// 不追求绝对匹配，只要能调用即可
			Method[] methods = clz.getMethods();
			for (Method m : methods) {
				try {
					if (!m.getName().equals(methodName)) continue;
					Class<?>[] parameterTypes = m.getParameterTypes();
					if (parameterTypes.length != params.length) continue;
					return m.invoke(null, params);
				} catch (Exception ignore) {}
			}
		}
		
		StringBuilder errorMsg = new StringBuilder("static method(");
		for (int i = 0; i < params.length; i++) {
			if (i != 0) errorMsg.append(", ");
			errorMsg.append(params[i] == null ? "null" : params[i].getClass().getName());
		}
		errorMsg.append(") not found in class[").append(clz.getCanonicalName()).append("]");
		throw new RuntimeException(errorMsg.toString());
	}
	
	/**
	 * 打印该类包含的变量名
	 * @param clz 类实例
	 */
	public static void printContainFieldNames(Class<?> clz) {
		Set<String> fieldSet = new HashSet<>();
		try {
			Field[] fields = clz.getFields(); // 公共变量（包含自父类继承的变量）
			for (Field field : fields) {
				fieldSet.add(field.getName());
			}
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}
		try {
			Field[] fields = clz.getDeclaredFields(); // 非公共变量（无法包含自父类继承的变量）
			for (Field field : fields) {
				fieldSet.add(field.getName());
			}
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}
		for (String string : fieldSet) {
			logger.debug(string);
		}
	}
	
	/**
	 * 打印类所在的文件路径
	 * <p> 适合代码多个有包含相同类的jar出现bug时，借用该方法排除</p>
	 * @param clz 类实例
	 */
	public static void printClassPath(Class<?> clz) {
		String classFilePath = clz.getName();
		classFilePath = classFilePath.replace('.', '/');
		classFilePath += ".class";
		
		printClassLoaderAndPath(clz.getClassLoader(), clz, classFilePath);
	}
	
	private static boolean hasClass(URL url, String classFilePath) {
		try {
			URL[] uls = {url};
			@SuppressWarnings("resource")
			URLClassLoader myLoader = new URLClassLoader(uls, null);
			URL resource = myLoader.getResource(classFilePath);
			return resource != null;
		} catch (Exception e) {
			return false;
		}
	}
	
	private static void printClassLoaderAndPath(ClassLoader classloader, Class<?> clz, String classFilePath) {
		if (classloader == null) {
			return;
		}
		logger.debug("classloader ==> " + classloader.toString());
		
		if (!(classloader instanceof URLClassLoader)) {
			return;
		}
		
		URLClassLoader urlClassLoader = (URLClassLoader) classloader;
		URL[] urls = urlClassLoader.getURLs();
		
		for (URL url : urls) {
			if (!hasClass(url, classFilePath)) {
				continue;
			}
			logger.debug(url.getPath());
		}
		logger.debug("------------------------------------------------");
		
		ClassLoader parent = classloader.getParent();
		printClassLoaderAndPath(parent, clz, classFilePath);
	}

	private static class Node {
		Class<?> clz;
		Node parent;
		List<Node> children;
		int cursor = 0;
	}

	private static void printParent(Class<?> clz) {
		Node node = getNode(null, clz);


		StringBuilder sb = new StringBuilder();

		int c = 0;
		Node p = node;
		addPrintStr(sb,c,node.clz);
		while (p != null) {
			while (p.children.size() > p.cursor) {
				p = p.children.get(p.cursor++);
				c++;
				addPrintStr(sb,c, p.clz);
			}
			p = p.parent;
			c--;
		}

		System.out.println(sb);
	}

	private static void addPrintStr(StringBuilder sb, int c, Class<?> clz) {
		for (int i = 0; i < c; i++) sb.append("    ");
		sb.append(clz.getCanonicalName());
		if (clz.isInterface()) {
			sb.append("  [I]");
		}
		sb.append("\n");
	}

	private static Node getNode(Node parent, Class<?> clz) {
		Node node = new Node();
		node.clz = clz;
		node.parent = parent;
		setChildren(node);
		return node;
	}

	private static void setChildren(Node node) {
		node.children = new ArrayList<>();
		if (node.clz == null) return;

		Class<?> clz = node.clz;
		Class<?> superclass = clz.getSuperclass();
		if (superclass != null) {
			node.children.add(getNode(node, superclass));
		}

		Class<?>[] interfaces = clz.getInterfaces();
		for (Class<?> anInterface : interfaces) {
			node.children.add(getNode(node, anInterface));
		}
	}

	public static void main(String[] args) {
		printClassPath(ReflectUtil.class);

	}
}














