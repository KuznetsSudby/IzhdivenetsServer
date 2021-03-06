package by.fastflow.controller;

import by.fastflow.Ajax;
import by.fastflow.DBModels.main.TaskItemDB;
import by.fastflow.DBModels.main.TaskListDB;
import by.fastflow.DBModels.TaskListPermissionsDB;
import by.fastflow.DBModels.main.UserDB;
import by.fastflow.repository.HibernateSessionFactory;
import by.fastflow.utils.Constants;
import by.fastflow.utils.ErrorConstants;
import by.fastflow.utils.RestException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.hibernate.Session;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Created by KuSu on 13.11.2016.
 */
@RestController
public class TaskListController extends ExceptionHandlerController {

    private static final String ADDRESS = Constants.DEF_SERVER + "tasklist";

    @RequestMapping(ADDRESS + "/test/")
    String home() {
        return "Hello World! " + ADDRESS;
    }


    @RequestMapping(value = ADDRESS + "/create", method = RequestMethod.POST)
    public
    @ResponseBody
    String create(@RequestHeader(value = "user_id") long userId,
                  @RequestHeader(value = "token") String token,
                  @RequestBody TaskListDB taskList) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();
            UserDB up = UserDB.getUser(session, userId, token);
            if (up.isChild())
                throw new RestException(ErrorConstants.NOT_CORRECT_USER_TYPE);

            session.beginTransaction();
            session.save(taskList
                    .validate()
                    .setUserId(userId)
                    .setListId(null));
            session.getTransaction().commit();
            session.close();
            return Ajax.successResponseJson(taskList.makeJson());
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(value = ADDRESS + "/update", method = RequestMethod.PUT)
    public
    @ResponseBody
    String update(@RequestHeader(value = "user_id") long userId,
                  @RequestHeader(value = "token") String token,
                  @RequestBody TaskListDB taskList) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();
            TaskListDB listDB = TaskListDB.getTaskList(session, taskList.getListId());
            if (listDB.getVisibility() != taskList.getVisibility())
                if (taskList.getVisibility() != Constants.TASK_LIST_ALL)
                    permissionInProgresInList(session, taskList.getListId());
            TaskListDB up = taskList.updateInBDWithToken(session, listDB, token);
            session.close();
            return Ajax.successResponseJson(up.makeJson());
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    private void permissionInProgresInList(Session session, long listId) throws RestException {
        if (session.createQuery("from TaskItemDB where listId = " + listId + " and state = " + Constants.TASK_ITEM_IN_PROGRESS).list().size() > 0)
            throw new RestException(ErrorConstants.TASK_IN_PROGRESS);
    }

    @RequestMapping(value = ADDRESS + "/permission/{tasklist_id}", method = RequestMethod.POST)
    public
    @ResponseBody
    String permission(@RequestHeader(value = "user_id") long userId,
                      @PathVariable(value = "tasklist_id") long taskListId,
                      @RequestHeader(value = "token") String token,
                      @RequestBody List<Long> gIds) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            TaskListDB list = TaskListDB.getTaskList(session, taskListId);
            UserDB up = UserDB.getUser(session, list.getUserId(), token);

            if (list.getVisibility() != Constants.TASK_LIST_ALLOWED_USERS)
                throw new RestException(ErrorConstants.WRONG_TASK_LIST_VISIBILITY);

            List<TaskItemDB> items = session.createQuery("from TaskItemDB where listId = " + taskListId + " and state = " + Constants.TASK_ITEM_IN_PROGRESS).list();

            session.beginTransaction();
            List<Object[]> users = getAllPermissionUsers(session, taskListId);
            for (Object[] user : users) {
                long us_gId = Constants.convertL(user[3]);
                if (gIds.contains(us_gId))
                    gIds.remove(us_gId);
                else {
                    long temp = Constants.convertL(user[4]);
                    for (TaskItemDB item : items)
                        if (item.getWorkingUser() == temp)
                            throw new RestException(ErrorConstants.TASK_IN_PROGRESS);
                    session.delete(TaskListPermissionsDB.createNew(list.getListId(), temp));
                }
            }

            for (Long gId : gIds) {
                List<Object[]> usAccept = getUserAccept(session, gId, userId);
                if (usAccept.size() == 0)
                    throw new RestException(ErrorConstants.NOT_HAVE_GID);
                if (Constants.convertL(usAccept.get(0)[2]) != Constants.USER_CHILD)
                    throw new RestException(ErrorConstants.NOT_CORRECT_USER_TYPE);
                if (Constants.convertL(usAccept.get(0)[0]) != Constants.RELATIONSHIP_ACCEPT)
                    throw new RestException(ErrorConstants.NOT_NAVE_PERMISSION);
                session.merge(TaskListPermissionsDB.createNew(list.getListId(), Constants.convertL(usAccept.get(0)[1])));
            }

            session.getTransaction().commit();

            List<Object[]> usrs = getAllPermissionUsers(session, taskListId);
            session.close();
            return Ajax.successResponseJson(getJsonPermissionUsers(usrs));
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(value = ADDRESS + "/permissionUsers/{tasklist_id}", method = RequestMethod.GET)
    public
    @ResponseBody
    String permissionUsers(@RequestHeader(value = "user_id") long userId,
                           @PathVariable(value = "tasklist_id") long taskListId,
                           @RequestHeader(value = "token") String token) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            TaskListDB list = TaskListDB.getTaskList(session, taskListId);
            UserDB up = UserDB.getUser(session, list.getUserId(), token);

            List<Object[]> users = getAllPermissionUsers(session, taskListId);

            session.close();
            return Ajax.successResponseJson(getJsonPermissionUsers(users));
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    private JsonArray getJsonPermissionUsers(List<Object[]> list) {
        JsonArray array = new JsonArray();
        for (Object[] objects : list) {
            array.add(UserDB.getJson((String) objects[0], (BigInteger) objects[1], (String) objects[2], (BigInteger) objects[3]));
        }
        return array;
    }

    private List<Object[]> getUserAccept(Session session, Long gId, long userId) {
        return session.createSQLQuery("SELECT " +
                "r.state as a0, u.user_id as a1, u.type as a2 " +
                "FROM izh_scheme.relationship r " +
                "JOIN izh_scheme.user u ON r.recipient_id = u.user_id " +
                "WHERE r.sender_id = " + userId + " " +
                "AND u.g_id = " + gId + " " +
                "UNION " +
                "SELECT " +
                "r.state as a0, u.user_id as a1, u.type as a2 " +
                "FROM izh_scheme.relationship r " +
                "JOIN izh_scheme.user u ON r.sender_id = u.user_id " +
                "WHERE r.recipient_id = " + userId + " " +
                "AND u.g_id = " + gId).list();
    }

    private List<Object[]> getAllPermissionUsers(Session session, long taskListId) {
        return session.createSQLQuery("SELECT " +
                "u.chat_name as a0, u.type as a1, u.photo as a2, u.g_id as a3, u.user_id as a4 " +
                "FROM izh_scheme.task_list_permissions r " +
                "JOIN izh_scheme.user u ON u.user_id = r.user_id " +
                "WHERE r.list_id = " + taskListId).list();
    }

    @RequestMapping(value = ADDRESS + "/delete/{tasklist_id}", method = RequestMethod.DELETE)
    public
    @ResponseBody
    Map<String, Object> delete(@RequestHeader(value = "token") String token,
                               @RequestHeader(value = "user_id") long userId,
                               @PathVariable(value = "tasklist_id") long tasklistId) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            TaskListDB listDB = TaskListDB.getTaskList(session, tasklistId);
            permissionInProgresInList(session, tasklistId);
            listDB.delete(session, token);

            return Ajax.emptyResponse();
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(value = ADDRESS + "/get", method = RequestMethod.GET)
    public
    @ResponseBody
    String getMy(@RequestHeader(value = "user_id") long userId,
                 @RequestHeader(value = "token") String token) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            UserDB up = UserDB.getUser(session, userId, token);

            JsonArray array = new JsonArray();
            if (up.isParent()) {
                List<Object[]> list = getMyList(session, userId);
                for (Object[] objects : list) {
                    JsonObject object = getListObject(objects);
                    object.add("user", null);
                    array.add(object);
                }
            } else {
                List<Object[]> list = getParentsList(session, userId);
                for (Object[] objects : list) {
                    JsonObject object = getListObject(objects);
                    object.add("user", UserDB.getJson((String) objects[9], (BigInteger) objects[10], (String) objects[11], (BigInteger) objects[12]));
                    array.add(object);
                }
            }
            session.close();
            return Ajax.successResponseJson(array);
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    private JsonObject getListObject(Object[] objects) {
        JsonObject object = new JsonObject();
        object.add("list", TaskListDB.getJson((BigInteger) objects[0], (BigInteger) objects[1], (String) objects[2], (String) objects[3]));
        object.addProperty("visible", objects[4] == null ? 0 : Constants.convertL(objects[4]));
        object.addProperty("inProgress", objects[5] == null ? 0 : Constants.convertL(objects[5]));
        object.addProperty("done", objects[6] == null ? 0 : Constants.convertL(objects[6]));
        object.addProperty("praised", objects[7] == null ? 0 : Constants.convertL(objects[7]));
        object.addProperty("total", objects[8] == null ? 0 : Constants.convertL(objects[8]));
        return object;
    }

    private List<Object[]> getParentsList(Session session, long userId) {
        return session.createSQLQuery("select " +
                "t_l.list_id as a0, t_l.visibility as a1, t_l.name  as a2, t_l.description AS a3, " +
                "count1 as a4, count2 as a5, count3 as a6, count4 as a7, totalcount as a8, u.chat_name as a9, u.type as a10, " +
                "u.photo as a11, u.g_id as a12 " +
                "from izh_scheme.relationship r " +
                "full join izh_scheme.task_list t_l on t_l.user_id = r.sender_id and visibility = " + Constants.TASK_LIST_ALL + " or " +
                "(visibility = " + Constants.TASK_LIST_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_list_permissions tlp where tlp.user_id = " + userId + " and tlp.list_id = t_l.list_id)!=0) " +
                "full join (select count(*) count1,list_id from izh_scheme.task_item t_i where (state = " + Constants.TASK_ITEM_VISIBLE + " and target = " + Constants.TASK_ITEM_WORK_ALL + ") or " +
                "(state = " + Constants.TASK_ITEM_VISIBLE + " and target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t1 on t1.list_id = t_l.list_id " +
                "full join (select count(*) count2,list_id from izh_scheme.task_item t_i where (state = " + Constants.TASK_ITEM_IN_PROGRESS + " and target = " + Constants.TASK_ITEM_WORK_ALL + ") " +
                "or (state = " + Constants.TASK_ITEM_IN_PROGRESS + " and target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t2 on t2.list_id = t_l.list_id " +
                "full join (select count(*) count3,list_id from izh_scheme.task_item t_i where (state = " + Constants.TASK_ITEM_DONE + " and target = " + Constants.TASK_ITEM_WORK_ALL + ") " +
                "or (state = " + Constants.TASK_ITEM_DONE + " and target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t3 on t3.list_id = t_l.list_id " +
                "full join (select count(*) count4,list_id from izh_scheme.task_item t_i where (state = " + Constants.TASK_ITEM_PRAISED + " and target = " + Constants.TASK_ITEM_WORK_ALL + ") " +
                "or (state = " + Constants.TASK_ITEM_PRAISED + " and target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t4 on t4.list_id = t_l.list_id " +
                "full join (select count(*) totalcount,list_id from izh_scheme.task_item t_i where target = " + Constants.TASK_ITEM_WORK_ALL + " " +
                "or (target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t5 on t5.list_id = t_l.list_id " +
                "join izh_scheme.user u on u.user_id = r.sender_id " +
                "where r.recipient_id = " + userId + " and r.state = " + Constants.RELATIONSHIP_ACCEPT + " " +
                "union " +
                "select " +
                "t_l.list_id as a0, t_l.visibility as a1, t_l.name  as a2, t_l.description AS a3, " +
                "count1 as a4, count2 as a5, count3 as a6, count4 as a7, totalcount as a8, u.chat_name as a9, u.type as a10, " +
                "u.photo as a11, u.g_id as a12 " +
                "from izh_scheme.relationship r " +
                "full join izh_scheme.task_list t_l on t_l.user_id = r.recipient_id and visibility = " + Constants.TASK_LIST_ALL + " or " +
                "(visibility = " + Constants.TASK_LIST_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_list_permissions tlp where tlp.user_id = " + userId + " and tlp.list_id = t_l.list_id)!=0) " +
                "full join (select count(*) count1,list_id from izh_scheme.task_item t_i where (state = " + Constants.TASK_ITEM_VISIBLE + " and target = " + Constants.TASK_ITEM_WORK_ALL + ") or " +
                "(state = " + Constants.TASK_ITEM_VISIBLE +
                " and target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t1 on t1.list_id = t_l.list_id " +
                "full join (select count(*) count2,list_id from izh_scheme.task_item t_i where (state = " + Constants.TASK_ITEM_IN_PROGRESS + " and target = " + Constants.TASK_ITEM_WORK_ALL + ") " +
                "or (state = " + Constants.TASK_ITEM_IN_PROGRESS + " and target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t2 on t2.list_id = t_l.list_id " +
                "full join (select count(*) count3,list_id from izh_scheme.task_item t_i where (state = " + Constants.TASK_ITEM_DONE + " and target = " + Constants.TASK_ITEM_WORK_ALL + ") " +
                "or (state = " + Constants.TASK_ITEM_DONE + " and target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t3 on t3.list_id = t_l.list_id " +
                "full join (select count(*) count4,list_id from izh_scheme.task_item t_i where (state = " + Constants.TASK_ITEM_PRAISED + " and target = " + Constants.TASK_ITEM_WORK_ALL + ") " +
                "or (state = " + Constants.TASK_ITEM_PRAISED + " and target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t4 on t4.list_id = t_l.list_id " +
                "full join (select count(*) totalcount,list_id from izh_scheme.task_item t_i where target = " + Constants.TASK_ITEM_WORK_ALL + " " +
                "or (target = " + Constants.TASK_ITEM_WORK_ALLOWED_USERS + " and (select count(*) from izh_scheme.task_permissions tp where tp.user_id = " + userId + " and tp.item_id = t_i.item_id)!=0) group by list_id) t5 on t5.list_id = t_l.list_id " +
                "join izh_scheme.user u on u.user_id = r.recipient_id " +
                "where r.sender_id = " + userId + " and r.state = " + Constants.RELATIONSHIP_ACCEPT + " "
        ).list();
    }

    private List<Object[]> getMyList(Session session, long userId) {
        return session.createSQLQuery("select distinct " +
                "tl.list_id as a0, tl.visibility as a1, tl.name as a2, tl.description AS a3, " +
                "count0 as a4, count1 as a5, count2 as a6, count3 as a7, totalcount as a8 " +
                "from izh_scheme.task_item ti " +
                "full join izh_scheme.task_list tl on ti.list_id = tl.list_id " +
                "full join (select count(item_id) count0, list_id from izh_scheme.task_item where state = " + Constants.TASK_ITEM_VISIBLE + " group by list_id ) t0 " +
                "on t0.list_id = tl.list_id " +
                "full join (select count(item_id) count1, list_id from izh_scheme.task_item where state = " + Constants.TASK_ITEM_IN_PROGRESS + " group by list_id ) t1 " +
                "on t1.list_id = tl.list_id " +
                "full join (select count(item_id) count2, list_id from izh_scheme.task_item where state = " + Constants.TASK_ITEM_DONE + " group by list_id ) t2 " +
                "on t2.list_id = tl.list_id " +
                "full join (select count(item_id) count3, list_id from izh_scheme.task_item where state = " + Constants.TASK_ITEM_PRAISED + " group by list_id ) t3 " +
                "on t3.list_id = tl.list_id " +
                "full join (select count(item_id) totalcount, list_id from izh_scheme.task_item group by list_id ) t4 " +
                "on t4.list_id = tl.list_id " +
                "where user_id = " + userId).list();
    }
}
