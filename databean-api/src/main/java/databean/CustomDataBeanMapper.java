package databean;

public interface CustomDataBeanMapper {
    Object create(Class<?> dataClass);
    Object copy(Object object, Class<?> dataClass);
}
