package by.fastflow.DBModels;

import by.fastflow.DBModels.pk.TaskPermissionsDBPK;

import javax.persistence.*;

/**
 * Created by KuSu on 22.10.2016.
 */
@Entity
@Table(name = "task_permissions", schema = "izh_scheme", catalog = "db")
@IdClass(TaskPermissionsDBPK.class)
public class TaskPermissionsDB {
    private long userId;
    private long itemId;

    @Id
    @Column(name = "user_id", nullable = false)
    public long getUserId() {
        return userId;
    }

    public TaskPermissionsDB setUserId(long userId) {
        this.userId = userId;
        return this;
    }

    @Id
    @Column(name = "item_id", nullable = false)
    public long getItemId() {
        return itemId;
    }

    public TaskPermissionsDB setItemId(long itemId) {
        this.itemId = itemId;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaskPermissionsDB that = (TaskPermissionsDB) o;

        if (userId != that.userId) return false;
        if (itemId != that.itemId) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (userId ^ (userId >>> 32));
        result = 31 * result + (int) (itemId ^ (itemId >>> 32));
        return result;
    }

    public static TaskPermissionsDB createNew(long itemId, long userId) {
        return new TaskPermissionsDB()
                .setUserId(userId)
                .setItemId(itemId);
    }
}
