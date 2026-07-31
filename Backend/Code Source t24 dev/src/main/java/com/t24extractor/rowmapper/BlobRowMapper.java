package com.t24extractor.rowmapper;

import com.t24extractor.tasklet.StandardSelectionTasklet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

/**
 * Mappe une ligne de vue BLOB en dictionnaire colonne → valeur.
 *
 * <p>Contrairement au mode XML, les colonnes ne sont pas connues à la
 * compilation : elles sont déterminées à l'exécution par
 * {@link StandardSelectionTasklet#getBlobViewFields()}, alimenté lors de la
 * construction de la vue.
 *
 * <p>Une colonne illisible est tracée puis ignorée plutôt que de faire échouer
 * le chunk entier : la vue peut contenir des champs absents de certaines lignes.
 */
public class BlobRowMapper implements RowMapper<HashMap<String, Object>>
{


    private static final Logger LOG = LoggerFactory.getLogger(BlobRowMapper.class) ;

    /**
     * {@inheritDoc}
     *
     * @return les champs de la vue, plus {@code _id} et {@code rowNumber}
     *         (ce dernier servant au découpage en partitions)
     */
    @Override
    public HashMap<String, Object> mapRow(ResultSet resultSet, int i) throws SQLException
    {
        HashMap<String, Object> t24BlobItem = new HashMap<>();
        t24BlobItem.put("_id", resultSet.getString("FIELD_ID"));
        for (String field : StandardSelectionTasklet.getBlobViewFields())
        {
        	try
        	{
        		t24BlobItem.put(field, resultSet.getObject(field));
        	}
        	catch(Exception ex)
        	{
        		LOG.trace("[BlobRowMapper] Erreur de mapping : {} " , field);
        	}    	
        }
        
        t24BlobItem.put("rowNumber", resultSet.getLong("rowNumber"));
        
        return(t24BlobItem) ;
    }
}
