package org.bukkit.craftbukkit.util;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.bukkit.Material;
import org.bukkit.plugin.AuthorNagException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * This file is imported from Commodore.
 *
 * @author md_5
 */
public class Commodore
{

    private static final Set<String> EVIL = new HashSet<>( Arrays.asList(
            "org/bukkit/World (III)I getBlockTypeIdAt",
            "org/bukkit/World (Lorg/bukkit/Location;)I getBlockTypeIdAt",
            "org/bukkit/block/Block ()I getTypeId",
            "org/bukkit/block/Block (I)Z setTypeId",
            "org/bukkit/block/Block (IZ)Z setTypeId",
            "org/bukkit/block/Block (IBZ)Z setTypeIdAndData",
            "org/bukkit/inventory/ItemStack ()I getTypeId",
            "org/bukkit/inventory/ItemStack (I)V setTypeId"
    ) );

    public static void main(String[] args)
    {
        OptionParser parser = new OptionParser();
        OptionSpec<File> inputFlag = parser.acceptsAll( Arrays.asList( "i", "input" ) ).withRequiredArg().ofType( File.class ).required();
        OptionSpec<File> outputFlag = parser.acceptsAll( Arrays.asList( "o", "output" ) ).withRequiredArg().ofType( File.class ).required();

        OptionSet options = parser.parse( args );

        File input = options.valueOf( inputFlag );
        File output = options.valueOf( outputFlag );

        if ( input.isDirectory() )
        {
            if ( !output.isDirectory() )
            {
                System.err.println( "If input directory specified, output directory required too" );
                return;
            }

            for ( File in : input.listFiles() )
            {
                if ( in.getName().endsWith( ".jar" ) )
                {
                    convert( in, new File( output, in.getName() ) );
                }
            }
        } else
        {
            convert( input, output );
        }
    }

    private static void convert(File in, File out)
    {
        System.out.println( "Attempting to convert " + in + " to " + out );

        try
        {
            try ( JarFile inJar = new JarFile( in, false ) )
            {
                JarEntry entry = inJar.getJarEntry( ".commodore" );
                if ( entry != null )
                {
                    return;
                }

                try ( JarOutputStream outJar = new JarOutputStream( new FileOutputStream( out ) ) )
                {
                    for ( Enumeration<JarEntry> entries = inJar.entries(); entries.hasMoreElements(); )
                    {
                        entry = entries.nextElement();

                        try ( InputStream is = inJar.getInputStream( entry ) )
                        {
                            byte[] b = ByteStreams.toByteArray( is );

                            if ( entry.getName().endsWith( ".class" ) )
                            {
                                b = convert( b, false );
                                entry = new JarEntry( entry.getName() );
                            }

                            outJar.putNextEntry( entry );
                            outJar.write( b );
                        }
                    }

                    outJar.putNextEntry( new ZipEntry( ".commodore" ) );
                }
            }
        } catch ( Exception ex )
        {
            System.err.println( "Fatal error trying to convert " + in );
            ex.printStackTrace();
        }
    }

    public static byte[] convert(byte[] b, final boolean modern)
    {
        ClassReader cr = new ClassReader( b );
        ClassWriter cw = new ClassWriter( cr, 0 );

        cr.accept( new ClassVisitor( Opcodes.ASM6, cw )
        {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
            {
                return new MethodVisitor( api, super.visitMethod( access, name, desc, signature, exceptions ) )
                {

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc)
                    {
                        if ( modern )
                        {
                            super.visitFieldInsn( opcode, owner, name, desc );
                            return;
                        }

                        if ( owner.equals( "org/bukkit/Material" ) )
                        {
                            try
                            {
                                Material.valueOf( "LEGACY_" + name );
                            } catch ( IllegalArgumentException ex )
                            {
                                throw new AuthorNagException( "No legacy enum constant for " + name + ". Did you forget to define api-version: 1.13 in your plugin.yml?" );
                            }

                            super.visitFieldInsn( opcode, owner, "LEGACY_" + name, desc );
                            return;
                        }

                        if ( owner.equals( "org/bukkit/Art" ) )
                        {
                            switch ( name )
                            {
                                case "BURNINGSKULL":
                                    super.visitFieldInsn( opcode, owner, "BURNING_SKULL", desc );
                                    return;
                                case "DONKEYKONG":
                                    super.visitFieldInsn( opcode, owner, "DONKEY_KONG", desc );
                                    return;
                            }
                        }

                        if ( owner.equals( "org/bukkit/Particle" ) )
                        {
                            switch ( name )
                            {
                                case "BLOCK_CRACK":
                                case "BLOCK_DUST":
                                case "FALLING_DUST":
                                    super.visitFieldInsn( opcode, owner, "LEGACY_" + name, desc );
                                    return;
                            }
                        }

                        super.visitFieldInsn( opcode, owner, name, desc );
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf)
                    {
                        if ( modern )
                        {
                            if ( owner.equals( "org/bukkit/Material" ) )
                            {
                                switch ( name )
                                {
                                    case "values":
                                        super.visitMethodInsn( opcode, "org/bukkit/craftbukkit/util/CraftLegacy", "modern_" + name, desc, itf );
                                        return;
                                }
                            }

                            super.visitMethodInsn( opcode, owner, name, desc, itf );
                            return;
                        }

                        if ( owner.equals( "org/bukkit/ChunkSnapshot" ) && name.equals( "getBlockData" ) && desc.equals( "(III)I" ) )
                        {
                            super.visitMethodInsn( opcode, owner, "getData", desc, itf );
                            return;
                        }

                        Type retType = Type.getReturnType( desc );

                        if ( EVIL.contains( owner + " " + desc + " " + name )
                                || ( owner.startsWith( "org/bukkit/block/" ) && ( desc + " " + name ).equals( "()I getTypeId" ) )
                                || ( owner.startsWith( "org/bukkit/block/" ) && ( desc + " " + name ).equals( "(I)Z setTypeId" ) ) )
                        {
                            Type[] args = Type.getArgumentTypes( desc );
                            Type[] newArgs = new Type[ args.length + 1 ];
                            newArgs[0] = Type.getObjectType( owner );
                            System.arraycopy( args, 0, newArgs, 1, args.length );

                            super.visitMethodInsn( Opcodes.INVOKESTATIC, "org/bukkit/craftbukkit/util/CraftEvil", name, Type.getMethodDescriptor( retType, newArgs ), false );
                            return;
                        }

                        if ( owner.equals( "org/bukkit/Material" ) )
                        {
                            if ( name.equals( "getMaterial" ) && desc.equals( "(I)Lorg/bukkit/Material;" ) )
                            {
                                super.visitMethodInsn( opcode, "org/bukkit/craftbukkit/util/CraftEvil", name, desc, itf );
                                return;
                            }

                            switch ( name )
                            {
                                case "values":
                                case "valueOf":
                                case "getMaterial":
                                case "matchMaterial":
                                    super.visitMethodInsn( opcode, "org/bukkit/craftbukkit/util/CraftLegacy", name, desc, itf );
                                    return;
                                case "ordinal":
                                    super.visitMethodInsn( Opcodes.INVOKESTATIC, "org/bukkit/craftbukkit/util/CraftLegacy", "ordinal", "(Lorg/bukkit/Material;)I", false );
                                    return;
                                case "name":
                                case "toString":
                                    super.visitMethodInsn( Opcodes.INVOKESTATIC, "org/bukkit/craftbukkit/util/CraftLegacy", name, "(Lorg/bukkit/Material;)Ljava/lang/String;", false );
                                    return;
                            }
                        }

                        if ( retType.getSort() == Type.OBJECT && retType.getInternalName().equals( "org/bukkit/Material" ) && owner.startsWith( "org/bukkit" ) )
                        {
                            super.visitMethodInsn( opcode, owner, name, desc, itf );
                            super.visitMethodInsn( Opcodes.INVOKESTATIC, "org/bukkit/craftbukkit/util/CraftLegacy", "toLegacy", "(Lorg/bukkit/Material;)Lorg/bukkit/Material;", false );
                            return;
                        }

                        super.visitMethodInsn( opcode, owner, name, desc, itf );
                    }
                };
            }
        }, 0 );

        return cw.toByteArray();
    }
}