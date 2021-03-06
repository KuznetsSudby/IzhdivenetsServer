package by.fastflow.controller;

import by.fastflow.Ajax;
import by.fastflow.DBModels.main.CardDB;
import by.fastflow.DBModels.main.UserDB;
import by.fastflow.DBModels.main.WishItemDB;
import by.fastflow.DBModels.main.WishListDB;
import by.fastflow.repository.HibernateSessionFactory;
import by.fastflow.utils.Constants;
import by.fastflow.utils.ErrorConstants;
import by.fastflow.utils.LIST;
import by.fastflow.utils.RestException;
import org.hibernate.Session;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Created by KuSu on 13.11.2016.
 */
@RestController
public class WishItemController extends ExceptionHandlerController {

    // TODO: 18.11.2016 загрузка фото

    private static final String ADDRESS = Constants.DEF_SERVER + "wishitem";

    @RequestMapping(value = ADDRESS + "/create/{wishlist_id}", method = RequestMethod.POST)
    public
    @ResponseBody
    String create(@RequestHeader(value = "user_id") long userId,
                               @RequestHeader(value = "token") String token,
                               @RequestBody WishItemDB wishitem) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            UserDB up = UserDB.getUser(session, WishListDB.getWishList(session, wishitem.getListId()).getUserId(), token);
            if (up.isParent())
                throw new RestException(ErrorConstants.NOT_CORRECT_USER_TYPE);

            session.beginTransaction();
            session.save(wishitem
                    .validate()
                    .setItemId(null));
            session.getTransaction().commit();
            session.close();
            return Ajax.successResponseJson(wishitem.makeJson());
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
                               @RequestBody WishItemDB wishItemDB) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            WishItemDB up = wishItemDB.updateInBDWithToken(session, WishItemDB.getWishItem(session, wishItemDB.getItemId()), token);

            session.close();
            return Ajax.successResponseJson(up.makeJson());
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(value = ADDRESS + "/delete/{wishitem_id}", method = RequestMethod.DELETE)
    public
    @ResponseBody
    Map<String, Object> delete(@RequestHeader(value = "token") String token,
                               @RequestHeader(value = "user_id") long userId,
                               @PathVariable(value = "wishitem_id") long wishitemId) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            WishItemDB.getWishItem(session, wishitemId).delete(session, token);

            return Ajax.emptyResponse();
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(value = ADDRESS + "/praised/{wishitem_id}", method = RequestMethod.POST)
    public
    @ResponseBody
    Map<String, Object> delete(@RequestHeader(value = "token") String token,
                               @RequestHeader(value = "user_id") long userId,
                               @RequestParam(value = "money") long money,
                               @RequestParam(value = "message") String message,
                               @PathVariable(value = "wishitem_id") long wishitemId) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            UserDB up = UserDB.getUser(session, userId, token);
            WishItemDB item = WishItemDB.getWishItem(session, wishitemId);
            WishListDB wishList = WishListDB.getWishList(session, item.getListId());

            RequestController.haveAcceptedRelationship(session, up.getUserId(), wishList.getUserId());
            CardDB card1 = (CardDB) session.createQuery("from CardDB where userId = " + up.getUserId()).list().get(0);
            CardDB card2 = (CardDB) session.createQuery("from CardDB where userId = " + wishList.getUserId()).list().get(0);
            if ((card1.getMoneyAmount() < money) || (money <= 0))
                throw new RestException(ErrorConstants.NEGATIVE_CARD_MONEY);
            session.beginTransaction();
            session.update(card1.sub(money));
            session.update(card2.add(money));
            session.getTransaction().commit();


            //todo еределать без костылей
            MessageController.generateMessage(session,
                    Constants.MSG_SEND_MONEY_FOR_ITEM,
                    userId,
                    DialogController.getTwainDialogId(session, userId, wishList.getUserId()),
                    new LIST()
                    .add(Constants.getStringMoney(money))
                    .add(item.getTitle())
                    .add(message)
            );
            return Ajax.emptyResponse();
        } catch (RestException re) {
            throw re;
        } catch (IndexOutOfBoundsException re) {
            throw new RestException(ErrorConstants.NOT_HAVE_CARD);
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(value = ADDRESS + "/get/{wishlist_id}", method = RequestMethod.GET)
    public
    @ResponseBody
    String getAll(@RequestHeader(value = "user_id") long userId,
                               @RequestHeader(value = "token") String token,
                               @PathVariable(value = "wishlist_id") long wishlistId) throws RestException {
        try {
            Session session = HibernateSessionFactory
                    .getSessionFactory()
                    .openSession();

            UserDB up = UserDB.getUser(session, userId, token);
            if (up.isParent()) {
                WishListDB wishListDB = WishListDB.getWishList(session, wishlistId);
                if (wishListDB.getVisibility() == Constants.WISH_ITEM_INVISIBLE)
                    throw new RestException(ErrorConstants.NOT_NAVE_PERMISSION);
                RequestController.haveAcceptedRelationship(session, userId, wishListDB.getUserId());
            } else {
                if (WishListDB.getWishList(session, wishlistId).getUserId() != userId)
                    throw new RestException(ErrorConstants.NOT_NAVE_PERMISSION);
            }

            List<WishItemDB> list = session.createQuery("from WishItemDB where listId = " + wishlistId +
                    (up.isParent() ? " and visibility = " + Constants.WISH_ITEM_VISIBLE : "")).list();

            session.close();
            return Ajax.successResponseJson(WishItemDB.makeJsonArray(list));
        } catch (RestException re) {
            throw re;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    @RequestMapping(ADDRESS + "/test/")
    String home() {
        return "Hello World! " + ADDRESS;
    }
}
