package com.campus.oj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("oj_user")
public class User {
    @TableId(type = IdType.AUTO) public Long id;
    public String username;
    public String password;
    @TableField("real_name")
    public String realName;
    public String role = "STUDENT";
    public String status = "ACTIVE";
    @TableField("create_time") public LocalDateTime createTime = LocalDateTime.now();
}
