package com.t24extractor.tasklet;

import com.t24extractor.model.FileControl;
import com.t24extractor.repository.t24_repo.FileControlRepository;
import com.t24extractor.utils.Renamer;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Implémentation adossée à la table T24 F_FILE_CONTROL.
 *
 * <p>Le nom reçu est d'abord normalisé (suppression du suffixe {@code $HIS} et
 * assimilés), F_FILE_CONTROL n'indexant que les tables de base.
 */
@Service
public class FileControlServiceImpl implements IFileControlService{


    private final FileControlRepository fileControlRepository;

    private final Renamer renamer = new Renamer();

    public FileControlServiceImpl(FileControlRepository fileControlRepository) {
        this.fileControlRepository = fileControlRepository;
    }

    /**
     * {@inheritDoc}
     *
     * @throws java.util.NoSuchElementException si la table est absente de
     *         F_FILE_CONTROL — sans classification, le nom Oracle ne peut pas
     *         être reconstitué
     */
    @Override
    public String classification(String recid) {

        FileControl fileControl = fileControlRepository.findFileControlByRecid(renamer.ssRenamer(recid)).orElseThrow(() -> new NoSuchElementException("Veuillez verifier que la table F_FILE_CONTROL est alimentee correctement"));
        return "INT".equalsIgnoreCase(fileControl.classification()) ? "F" : "FBNK";
    }
}
