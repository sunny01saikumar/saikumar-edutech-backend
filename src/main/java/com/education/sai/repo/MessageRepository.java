package com.education.sai.repo;

import com.education.sai.model.MessageClass;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository
        extends JpaRepository<MessageClass, Long> {

    @Query("""

        SELECT m FROM MessageClass m

        WHERE

        (m.senderId = :user1
        AND m.receiverId = :user2)

        OR

        (m.senderId = :user2
        AND m.receiverId = :user1)

        ORDER BY m.createdAt ASC

    """)
    List<MessageClass> getConversation(

            @Param("user1") Long user1,

            @Param("user2") Long user2
    );
}