package com.example.youkeda_project.mapper;

import com.example.youkeda_project.entity.ChatHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatHistoryMapper {

    @Insert("INSERT INTO chat_history (user_id, role, content, tool_call_id, name, tool_calls) VALUES (#{userId}, #{role}, #{content}, #{toolCallId}, #{name}, #{toolCalls})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatHistory chatHistory);

    @Select("SELECT id, user_id, role, content, tool_call_id, name, tool_calls, created_at FROM chat_history WHERE user_id = #{userId} ORDER BY id ASC")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "role", column = "role"),
        @Result(property = "content", column = "content"),
        @Result(property = "toolCallId", column = "tool_call_id"),
        @Result(property = "name", column = "name"),
        @Result(property = "toolCalls", column = "tool_calls"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<ChatHistory> selectByUserId(@Param("userId") String userId);

    @Select("SELECT id, user_id, role, content, tool_call_id, name, tool_calls, created_at FROM chat_history ORDER BY id DESC")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "role", column = "role"),
        @Result(property = "content", column = "content"),
        @Result(property = "toolCallId", column = "tool_call_id"),
        @Result(property = "name", column = "name"),
        @Result(property = "toolCalls", column = "tool_calls"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<ChatHistory> selectAll();

    @Delete("DELETE FROM chat_history WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") String userId);

    @Delete("DELETE FROM chat_history WHERE user_id = #{userId} AND id NOT IN (SELECT id FROM chat_history WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{keepLast})")
    int trimHistory(@Param("userId") String userId, @Param("keepLast") int keepLast);

    @Select("SELECT COUNT(*) FROM chat_history")
    int count();
}
