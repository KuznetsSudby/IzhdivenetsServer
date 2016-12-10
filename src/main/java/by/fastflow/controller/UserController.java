package by.fastflow.controller;

import by.fastflow.Ajax;
import by.fastflow.DBModels.main.AuthDB;
import by.fastflow.DBModels.main.UserDB;
import by.fastflow.repository.HibernateSessionFactory;
import by.fastflow.utils.Constants;
import by.fastflow.utils.ErrorConstants;
import by.fastflow.utils.RestException;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.account.UserSettings;
import org.hibernate.Session;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Created by KuSu on 01.07.2016.
 */
@RestController
public class UserController extends ExceptionHandlerController {

    // TODO: 18.11.2016 загрузка фото

    private static final String ADDRESS = Constants.DEF_SERVER + "user";

    @RequestMapping(value = ADDRESS + "/loginVk", method = RequestMethod.POST)
    public
    @ResponseBody
    String loginVk(@RequestHeader(value = "token") String token, @RequestHeader(value = "userId") int userId) throws RestException {
        try {
            TransportClient transportClient = HttpTransportClient.getInstance();
            VkApiClient vk = new VkApiClient(transportClient);
            UserActor actor = new UserActor(userId, token);
            UserSettings userSettings = vk.account().getProfileInfo(actor).execute();

            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();
            List<AuthDB> list = session.createQuery("from AuthDB where type = " + Constants.LOGIN_TYPE_VK + " and token = '" + (userId + "'")).list();

            if (list.size() == 0) {
                throw new RestException(ErrorConstants.NOT_HAVE_ID);
            }

            session.beginTransaction();
            UserDB userDB = UserDB.getUser(session, list.get(0).getUserId());
            userDB.updateToken();
            session.update(userDB);
            session.getTransaction().commit();
            session.close();

            return Ajax.successResponseJson(userDB.makeFullJson());
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(value = ADDRESS + "/registerVk", method = RequestMethod.POST)
    public
    @ResponseBody
    String registerVk(@RequestHeader(value = "token") String token, @RequestHeader(value = "userId") int userId, @RequestParam(value = "type") int type) throws RestException {
        try {
            TransportClient transportClient = HttpTransportClient.getInstance();
            VkApiClient vk = new VkApiClient(transportClient);
            UserActor actor = new UserActor(userId, token);
            UserSettings userSettings = vk.account().getProfileInfo(actor).execute();

            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();
            List<AuthDB> list = session.createQuery("from AuthDB where type = " + Constants.LOGIN_TYPE_VK + " and token = '" + (userId + "'")).list();
            UserDB userDB = null;
            session.beginTransaction();
            if (list.size() == 0) {
                userDB = UserDB
                        .createNew(userSettings.getFirstName() + " " + userSettings.getLastName(), type)
                        .setUserId(null);
                session.save(userDB);
                session.saveOrUpdate(AuthDB
                        .createNew(Constants.LOGIN_TYPE_VK, userId + "", userDB.getUserId()));
                session.saveOrUpdate(CardController.createCard(userDB));
            } else {
                userDB = UserDB.getUser(session, list.get(0).getUserId())
                        .updateToken();
                session.update(userDB);
            }
            session.getTransaction().commit();
            session.close();
            return Ajax.successResponseJson(userDB.makeFullJson());
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
                  @RequestBody UserDB user,
                  @RequestHeader(value = "token") String token) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();
            UserDB up = user.updateInBDWithToken(session, UserDB.getUser(session, userId), token);
            session.close();
            return Ajax.successResponseJson(up.makeFullJson());
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(value = ADDRESS + "/delete", method = RequestMethod.DELETE)
    public
    @ResponseBody
    Map<String, Object> delete(@RequestHeader(value = "user_id") long userId,
                               @RequestHeader(value = "token") String token) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();
            UserDB.getUser(session, userId, token).delete(session, token);
            return Ajax.emptyResponse();
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }
/*
    @RequestMapping(value = ADDRESS + "/getUserInfo/{user_id}", method = RequestMethod.PUT)
    public
    @ResponseBody
    Map<String, Object> getUser(@PathVariable(value = "user_id") long userId) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();
            return Ajax.successResponse((UserDB.getUser(session, userId)).anonimize());
        } catch (RestException re) {
            throw re;
        } catch (ObjectNotFoundException e) {
            throw new RestException(ErrorConstants.NOT_HAVE_ID);
        } catch (Exception e) {
            throw new RestException(e);
        }
    }*/


    @RequestMapping(ADDRESS + "/test/")
    String home() {
        return "Hello World! " + ADDRESS;
    }
}