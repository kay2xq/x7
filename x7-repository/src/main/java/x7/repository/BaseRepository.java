package x7.repository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import x7.core.async.CasualWorker;
import x7.core.async.IAsyncTask;
import x7.core.bean.Criteria;
import x7.core.bean.IQuantity;
import x7.core.bean.Parsed;
import x7.core.util.StringUtil;
import x7.core.web.Pagination;
import x7.repository.exception.PersistenceException;
import x7.repository.mapper.Mapper;
import x7.repository.mapper.MapperFactory;
import x7.repository.redis.JedisConnector_Persistence;

/**
 * 
 * Biz Repository extends BaseRepository
 * 
 * @author Sim
 *
 * @param <T>
 */
public abstract class BaseRepository<T> implements X7Repository<T> {

	public final static String ID_MAP_KEY = "ID_MAP_KEY";

	public Map<String, String> map = new HashMap<String, String>();

	private Class<T> clz;
	
	protected Class<T> getClz(){
		return clz;
	}

	public BaseRepository() {
		parse();
	}
	
	private void parse(){
		
		Type genType = getClass().getGenericSuperclass();

		Type[] params = ((ParameterizedType) genType).getActualTypeArguments();
		
		this.clz = (Class)params[0];
		
		System.out.println("______BaseRepository, " + this.clz.getName());
		HealthChecker.repositoryList.add(this);
	}

	protected Object preMapping(String methodName, Object... s) {

		boolean isOne = methodName.startsWith("get");

		String sql = map.get(methodName);
		if (StringUtil.isNullOrEmpty(sql)) {

			methodName = methodName.replace("list", "").replace("get", "").replace("find", "")
					.replace("from", " ")
					.replace("By", " where ");
			methodName = methodName.replace("And", " = ? and ").replace("Or", " = ? or ");

			Parsed parsed = x7.core.bean.Parser.get(clz);
			String clzName = parsed.getClzName();
			String tableName = parsed.getTableName();
			
			methodName = methodName.toLowerCase();
			
			StringBuilder sb = new StringBuilder();
			sb.append("select * from ");
			
			if (methodName.contains(clzName)){
				methodName.replace(clzName, tableName);
			}else{
				sb.append(parsed.getTableName()).append(" ");
			}
			
			sb.append(methodName).append(" = ?");

			sql = sb.toString();

			map.put(methodName, sql);

		}
		List<Object> conditionList = Arrays.asList(s);
		List<T> list = (List<T>) ManuRepository.list(clz, sql, conditionList);

		if (isOne) {
			if (list.isEmpty())
				return null;
			return list.get(0);
		}

		return list;
	}

	@Override
	public void set(byte[] key, byte[] value) {
		JedisConnector_Persistence.getInstance().set(key, value);
	}

	@Override
	public byte[] get(byte[] key) {
		return JedisConnector_Persistence.getInstance().get(key);
	}

	@Override
	public void set(String key, String value, int seconds) {
		JedisConnector_Persistence.getInstance().set(key, value, seconds);
	}

	@Override
	public void set(String key, String value) {
		JedisConnector_Persistence.getInstance().set(key, value);
	}

	@Override
	public String get(String key) {
		return JedisConnector_Persistence.getInstance().get(key);
	}

	@Override
	public long createId(Object obj) {
		
		final String name = obj.getClass().getName();
		final long id = JedisConnector_Persistence.getInstance().hincrBy(ID_MAP_KEY, name, 1);

		if (id == 0) {
			throw new PersistenceException("UNEXPECTED EXCEPTION WHILE CREATING ID");
		}

		CasualWorker.accept(new IAsyncTask(){

			@Override
			public void execute() throws Exception {
				IdGenerator generator = new IdGenerator();
				generator.setClzName(name);
				List<IdGenerator> list = Repositories.getInstance().list(generator);
				if (list.isEmpty()){
					generator.setMaxId(id);
					Repositories.getInstance().create(generator);
				}else{
					generator.setMaxId(id);
					Repositories.getInstance().refresh(generator);
				}
			}
			
		});

		return id;
	}

	@Override
	public int reduce(IQuantity obj, int reduced) {
		if (reduced < 0) {
			throw new RuntimeException("reduced quantity must > 0");
		}

		String mapKey = obj.getClass().getName();

		int quantity = (int) JedisConnector_Persistence.getInstance().hincrBy(mapKey, obj.getKey(), -reduced);

		obj.setQuantity(quantity);

		return quantity;
	}
	
	
	@Override
	public boolean createBatch(List<T> objList){
		return Repositories.getInstance().createBatch(objList);
	}

	@Override
	public long create(T obj) {
		/*
		 * FIXME
		 */
		System.out.println("BaesRepository.create: " + obj);

		long id = Repositories.getInstance().create(obj);

		return id;

	}


	@Override
	public boolean refresh(T obj) {
		return Repositories.getInstance().refresh(obj);
	}

	@Override
	public boolean refresh(T obj, Map<String, Object> conditionMap) {
		return Repositories.getInstance().refresh(obj, conditionMap);
	}

	@Override
	public void remove(T obj) {
		Repositories.getInstance().remove(obj);
	}

	@Override
	public T get(long idOne) {

		return Repositories.getInstance().get(clz, idOne);
	}


	@Override
	public List<T> list() {

		return Repositories.getInstance().list(clz);
	}

	@Override
	public List<T> list(T conditionObj) {

		if (conditionObj instanceof Criteria.Fetch) {
			throw new RuntimeException(
					"Exception supported, no pagination not to invoke Repositories.getInstance().list(criteriaJoinalbe);");
		}

		return Repositories.getInstance().list(conditionObj);
	}

	@Override
	public Pagination<Map<String, Object>> list(Criteria.Fetch criteria, Pagination<Map<String, Object>> pagination) {

		return Repositories.getInstance().list(criteria, pagination);
	}


	@Override
	public long getMaxId() {
		return Repositories.getInstance().getMaxId(clz);
	}

	@Override
	public long getMaxId(T conditionObj) {
		return Repositories.getInstance().getMaxId(conditionObj);
	}

	@Override
	public long getCount(T conditonObj) {
		return Repositories.getInstance().getCount(conditonObj);
	}

	@Override
	public T getOne(T conditionObj, String orderBy, String sc) {

		return Repositories.getInstance().getOne(conditionObj, orderBy, sc);
	}

	@Override
	public T getOne(T conditionObj) {

		T t = Repositories.getInstance().getOne(conditionObj);
		return t;
	}

	@Override
	public void refreshCache() {
		Repositories.getInstance().refreshCache(clz);
	}

	@Override
	public Object getSum(T conditionObj, String sumProperty) {
		return Repositories.getInstance().getSum(conditionObj, sumProperty);
	}

	@Override
	public Object getSum(T conditionObj, String sumProperty, Criteria criteria) {
		return Repositories.getInstance().getSum(sumProperty, criteria);
	}

	@Override
	public Object getCount(String countProperty, Criteria criteria) {
		return Repositories.getInstance().getCount(countProperty, criteria);
	}

	@Override
	public List<T> in(List<? extends Object> inList) {
		if (inList.isEmpty())
			return new ArrayList<T>();
		Set<Object> set = new HashSet<Object>();
		for (Object obj : inList){
			set.add(obj);
		}
		
		List<Object> list = new ArrayList<Object>();
		for (Object obj : set){
			list.add(obj);
		}
		
		return Repositories.getInstance().in(clz, list);
	}

	@Override
	public List<T> in(String inProperty, List<? extends Object> inList) {
		if (inList.isEmpty())
			return new ArrayList<T>();
		
		Set<Object> set = new HashSet<Object>();
		for (Object obj : inList){
			set.add(obj);
		}
		
		List<Object> list = new ArrayList<Object>();
		for (Object obj : set){
			list.add(obj);
		}
		
		return Repositories.getInstance().in(clz, inProperty, list);
	}

	@Override
	public Pagination<T> list(Criteria criteria, Pagination<T> pagination) {

		return Repositories.getInstance().list(criteria, pagination);
	}

	
	public static class  HealthChecker {
		
		
		private static List<BaseRepository> repositoryList = new ArrayList<BaseRepository>();
		
		protected static void onStarted (){
			
			for (BaseRepository repository : repositoryList) {

				try{
					Class clz = repository.getClz();
					String sql = MapperFactory.tryToCreate(clz);
					String test = MapperFactory.getSql(clz, Mapper.CREATE);
					if (StringUtil.isNullOrEmpty(test)){
						System.out.println("FAILED TO START X7-REPOSITORY, check Bean: " + clz);
						System.exit(1);
					}
					
					Repositories.getInstance().execute(clz.newInstance(), sql);
					
				}catch (Exception e) {
					
				}
			}
		}
	}
}
