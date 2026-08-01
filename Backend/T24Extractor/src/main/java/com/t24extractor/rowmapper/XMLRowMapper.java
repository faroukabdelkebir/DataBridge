package com.t24extractor.rowmapper;

import com.t24extractor.model.T24Table;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Mappe une ligne {@code (RECID, XMLRECORD)} en {@link T24Table}.
 *
 * <p>Les colonnes sont lues par position, l'ordre étant fixé par la requête
 * construite dans {@link com.t24extractor.tasklet.QueryTasklet}.
 */
public class XMLRowMapper implements RowMapper<T24Table>
{
    /**
     * {@inheritDoc}
     *
     * @return l'enregistrement T24 brut, non décodé
     */
    @Override
    public T24Table mapRow(ResultSet resultSet, int i) throws SQLException
    {
    	T24Table t24XMLItem = new T24Table();
        
    	t24XMLItem.setRecid(resultSet.getString(1));
    	t24XMLItem.setXmlRecord(resultSet.getString(2));

        return(t24XMLItem) ;
    }
}
