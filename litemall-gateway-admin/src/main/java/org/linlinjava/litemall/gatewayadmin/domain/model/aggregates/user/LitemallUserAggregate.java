package org.linlinjava.litemall.gatewayadmin.domain.model.aggregates.user;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.gatewayadmin.domain.valueobjects.user.LitemallUserId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Setter
@Getter
public class LitemallUserAggregate {


    private LitemallUserId userId;
    private String username;
    private String password;
    private Byte gender;
    private LocalDate birthday;
    private LocalDate lastLoginTime;
    private String lastLoginIp;
    private String userLevel;
    private String avatar;
    private String sessionKey;
    private String weixinOpenid;
    private boolean isAdmin;
    private int status;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private boolean deleted;


    // Custom deserializer for the array-based dates
    @JsonSetter("addTime")
    public void setAddTime(List<Integer> dateParts) {
        if (dateParts != null && dateParts.size() >= 6) {
            this.addTime = LocalDateTime.of(
                    dateParts.get(0), dateParts.get(1), dateParts.get(2),
                    dateParts.get(3), dateParts.get(4), dateParts.get(5)
            );
        }
    }

    @JsonSetter("updateTime")
    public void setUpdateTime(List<Integer> dateParts) {
        if (dateParts != null && dateParts.size() >= 6) {
            this.updateTime = LocalDateTime.of(
                    dateParts.get(0), dateParts.get(1), dateParts.get(2),
                    dateParts.get(3), dateParts.get(4), dateParts.get(5)
            );
        }
    }

}
