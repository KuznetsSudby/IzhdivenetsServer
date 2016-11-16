package by.fastflow.utils;

import by.fastflow.Ajax;
import by.fastflow.controller.ExceptionHandlerController;
import org.hibernate.Session;

import java.util.Map;

/**
 * Created by KuSu on 27.10.2016.
 */
public abstract class UpdatableDB<T extends UpdatableDB> extends NextableId<T> {

    public abstract void updateBy(T up);

    public T updateInBDWithToken(Session session, T up, String token) throws RestException {
        validate();
        up.havePermissionToModify(session, token);
        up.updateBy(this);
        session.beginTransaction();
        session.update(up);
        session.getTransaction().commit();
        return up;
    }

    public abstract void validate() throws RestException;

    public void delete(Session session, String token) throws RestException {
        havePermissionToDelete(session, token);
        session.beginTransaction();
        session.delete(this);
        session.getTransaction().commit();
    }

    public abstract void havePermissionToModify(Session session, String token) throws RestException;
    public abstract void havePermissionToDelete(Session session, String token) throws RestException;
}
